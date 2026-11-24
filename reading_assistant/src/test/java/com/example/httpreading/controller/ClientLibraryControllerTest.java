package com.example.httpreading.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.service.BooksService;

class ClientLibraryControllerTest {
    @Test
    void listsOnlyDownloadableBookMetadataWithoutServerPaths() {
        BooksService service = org.mockito.Mockito.mock(BooksService.class);
        Books book = new Books();
        book.setId(3L);
        book.setTitle("Example");
        book.setAuthor("Writer");
        book.setSourceFormat("epub");
        book.setSourceFilePath("/private/server/book.epub");
        Books unavailable = new Books();
        unavailable.setId(4L);
        when(service.requestBooks(null, null, 0, 20, "updatedAt", "desc"))
            .thenReturn(new PageImpl<>(List.of(book, unavailable)));

        var result = new ClientLibraryController(service, null, null).list(0, 1L);

        assertThat(result.getData().books()).hasSize(1);
        assertThat(result.getData().books().get(0).title()).isEqualTo("Example");
        assertThat(result.getData().books().get(0).toString()).doesNotContain("/private/server");
    }
}
