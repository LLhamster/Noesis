package com.example.httpreading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.httpreading.repository.BooksRepository;
import com.example.httpreading.service.CloudBookImportService.Decision;
import com.example.httpreading.service.CloudBookImportService.Status;

@DataJpaTest
@Import(CloudBookImportService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CloudBookImportPersistenceTest {
    @Autowired
    private CloudBookImportService service;

    @Autowired
    private BooksRepository books;

    @Test
    void concurrentEditionsReserveOnlyOneCloudFileAndCompletionIsIdempotent() throws Exception {
        var start = new CountDownLatch(1);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        var first = CompletableFuture.supplyAsync(() -> prepare(start, 1L, firstId, "测试作品", "a"));
        var second = CompletableFuture.supplyAsync(() -> prepare(start, 2L, secondId, "测试作品（新版）", "b"));
        start.countDown();

        Decision firstDecision = first.get(15, TimeUnit.SECONDS);
        Decision secondDecision = second.get(15, TimeUnit.SECONDS);
        assertEquals(Set.of(Status.READY, Status.IN_PROGRESS),
                Set.of(firstDecision.status(), secondDecision.status()));

        boolean firstWon = firstDecision.status() == Status.READY;
        UUID importId = firstWon ? firstId : secondId;
        long owner = firstWon ? 1L : 2L;
        long other = firstWon ? 2L : 1L;
        assertThrows(ResponseStatusException.class,
                () -> service.complete(other, importId, "测试作品", "测试作者", null));
        Long bookId = service.complete(owner, importId, "测试作品", "测试作者", null);
        assertEquals(bookId, service.complete(owner, importId, "测试作品", "测试作者", null));
        var saved = books.findById(bookId).orElseThrow();
        assertEquals(owner, saved.getCloudOwnerId());
        assertEquals("/Readest/noesis/" + importId + ".epub", saved.getCloudDrivePath());
        String syncPath = "/Readest/books/" + "d".repeat(32) + "/测试作品.epub";
        assertEquals(bookId, service.complete(owner, importId, "测试作品", "测试作者", syncPath));
        assertEquals(syncPath, books.findById(bookId).orElseThrow().getCloudDrivePath());
        assertEquals(bookId, service.complete(owner, importId, "测试作品", "测试作者", null));
        assertEquals(syncPath, books.findById(bookId).orElseThrow().getCloudDrivePath());
        assertThrows(ResponseStatusException.class, () -> service.complete(
                owner, importId, "测试作品", "测试作者", "/Readest/library.json"));
        assertNull(saved.getSourceFilePath());
        assertEquals(bookId, service.prepare(other, UUID.randomUUID(), "测试作品（第二版）",
                "测试作者", "c".repeat(64)).bookId());
    }

    private Decision prepare(CountDownLatch start, Long userId, UUID requestId, String title, String hash) {
        try {
            if (!start.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent import did not start");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
        return service.prepare(userId, requestId, title, "测试作者", hash.repeat(64));
    }
}
