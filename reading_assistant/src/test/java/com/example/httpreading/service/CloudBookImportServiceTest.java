package com.example.httpreading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.domain.entity.CloudBookImport;
import com.example.httpreading.repository.BooksRepository;
import com.example.httpreading.repository.CloudBookImportRepository;
import com.example.httpreading.service.CloudBookImportService.Status;

class CloudBookImportServiceTest {
    private final BooksRepository books = mock(BooksRepository.class);
    private final CloudBookImportRepository imports = mock(CloudBookImportRepository.class);
    private final CloudBookImportService service = new CloudBookImportService(books, imports);
    private final String hash = "a".repeat(64);

    @Test
    void reusesAnExistingEditionBeforeReserving() {
        Books book = new Books();
        book.setId(7L);
        book.setTitle("看见（第２版）");
        book.setAuthor("柴静");
        when(books.findAll()).thenReturn(List.of(book));

        var result = service.prepare(1L, UUID.randomUUID(), "看见", "柴静", hash);
        assertEquals(Status.EXISTS, result.status());
        assertEquals(7L, result.bookId());
        verifyNoInteractions(imports);
    }

    @Test
    void asksForConfirmationWhenAuthorIsMissing() {
        assertEquals(Status.NEEDS_CONFIRMATION,
                service.prepare(1L, UUID.randomUUID(), "看见", null, hash).status());
        verifyNoInteractions(imports);
    }

    @Test
    void onlyTheOriginalRequestCanResumeTheReservation() {
        UUID request = UUID.randomUUID();
        when(imports.saveAndFlush(any())).thenAnswer(call -> {
            CloudBookImport reservation = call.getArgument(0);
            when(imports.findById(request)).thenReturn(Optional.of(reservation));
            when(imports.findByWorkKey(reservation.getWorkKey())).thenReturn(Optional.of(reservation));
            return reservation;
        });

        assertEquals(Status.READY, service.prepare(1L, request, "看见", "柴静", hash).status());
        assertEquals(Status.READY, service.prepare(1L, request, "看见", "柴静", hash).status());
        assertEquals(Status.IN_PROGRESS,
                service.prepare(2L, request, "看见", "柴静", hash).status());
        assertEquals(Status.IN_PROGRESS,
                service.prepare(2L, UUID.randomUUID(), "看见（新版）", "柴静", "b".repeat(64)).status());
    }

    @Test
    void completesTheReservationOnceWithoutStoringTheEpubOnTheServer() {
        UUID request = UUID.randomUUID();
        String key = java.util.HexFormat.of().formatHex(digest("2:看见柴静"));
        CloudBookImport entry = new CloudBookImport(request, key, hash, 1L);
        when(imports.findForCompletion(request, 1L)).thenReturn(Optional.of(entry));
        when(books.saveAndFlush(any())).thenAnswer(call -> {
            Books book = call.getArgument(0);
            assertEquals("/Readest/noesis/" + request + ".epub", book.getCloudDrivePath());
            assertEquals(null, book.getSourceFilePath());
            book.setId(7L);
            return book;
        });

        assertEquals(7L, service.complete(1L, request, "看见", "柴静", null));
        assertEquals(7L, service.complete(1L, request, "看见", "柴静", null));
        verify(books, times(1)).saveAndFlush(any());
    }

    private static byte[] digest(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
