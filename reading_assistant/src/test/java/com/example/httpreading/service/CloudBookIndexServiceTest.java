package com.example.httpreading.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.repository.BooksRepository;
import com.example.httpreading.repository.ChaptersRepository;
import com.example.httpreading.service.CloudBookIndexService.Section;

class CloudBookIndexServiceTest {
    private final BooksRepository books = mock(BooksRepository.class);
    private final ChaptersRepository chapters = mock(ChaptersRepository.class);
    private final RagService rag = mock(RagService.class);
    private final CloudBookIndexService service = new CloudBookIndexService(books, chapters, rag);
    private final List<Section> sections = List.of(new Section(2, "Chapter 2", "Searchable text"));

    @Test
    void preparesIndexForOwnerAndMarksItReady() {
        Books book = new Books();
        book.setTitle("Example");
        when(books.findOwnedCloudBookForUpdate(3L, 1L)).thenReturn(Optional.of(book));
        when(rag.rebuildIndexForBook(eq(3L), anyList(), eq("Example"))).thenAnswer(call -> {
            List<com.example.httpreading.domain.entity.Chapters> saved = call.getArgument(1);
            assertEquals(2, saved.get(0).getChapterIndex());
            assertEquals("Searchable text", saved.get(0).getContent());
            return 1;
        });

        service.prepare(3L, 1L, sections);
        assertEquals("READY", book.getParseStatus());
    }

    @Test
    void deniesUsersWhoDoNotOwnTheCloudBook() {
        assertThrows(ResponseStatusException.class, () -> service.prepare(3L, 2L, sections));
        verifyNoInteractions(chapters, rag);
    }
}
