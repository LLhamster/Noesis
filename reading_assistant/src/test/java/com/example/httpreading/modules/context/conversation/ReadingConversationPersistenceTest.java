package com.example.httpreading.modules.context.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.httpreading.common.web.BusinessException;
import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.repository.BooksRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({ReadingConversationService.class, ObjectMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReadingConversationPersistenceTest {
    @Autowired
    private ReadingConversationService service;
    @Autowired
    private BooksRepository books;

    @Test
    void concurrentFirstOpensReuseOneDefaultConversation() throws Exception {
        Long book = createBook();
        var start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> listAfter(start, book));
        var second = CompletableFuture.supplyAsync(() -> listAfter(start, book));
        start.countDown();
        assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertEquals(1, service.list(42L, book).size());
    }

    @Test
    void historyIsBookAndAccountScopedAndOnlyCompleteTurnsFeedContext() {
        Long book = createBook();
        String original = service.list(42L, book).get(0).id();
        Long firstTurn = save(book, original, 1, "第一章问题", "completed");
        save(book, original, 2, "第二章追问", "model_unavailable");
        var history = service.open(42L, book, original, null).turns();
        assertEquals(List.of(1, 2), history.stream().map(ReadingConversationService.TurnView::chapterIndex).toList());
        assertEquals(firstTurn, history.get(0).id());
        assertEquals(List.of("章节引用"), history.get(0).sources());
        assertTrue(service.recentDialogue(42L, book, original).contains("第一章问题"));
        assertFalse(service.recentDialogue(42L, book, original).contains("第二章追问"));
        assertThrows(BusinessException.class, () -> service.open(99L, book, original, null));
        Long otherBook = createBook();
        assertThrows(BusinessException.class, () -> service.open(42L, otherBook, original, null));

        var created = service.create(42L, book);
        assertTrue(service.open(42L, book, created.id(), null).turns().isEmpty());
        service.open(42L, book, original, null);
        assertEquals(original, service.list(42L, book).get(0).id());
    }

    private String listAfter(CountDownLatch start, Long book) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
            return service.list(42L, book).get(0).id();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private Long createBook() {
        Books book = new Books();
        book.setTitle("书籍");
        return books.save(book).getId();
    }

    private Long save(Long book, String conversation, int chapter, String question, String status) {
        AiChatRequest request = new AiChatRequest();
        request.setBookId(book);
        request.setConversationId(conversation);
        request.setChapterIndex(chapter);
        request.setQuestion(question);
        AiChatResponse response = new AiChatResponse("回答", List.of("章节引用"));
        response.setStatus(status);
        return service.saveTurn(42L, request, response);
    }
}
