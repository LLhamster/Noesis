package com.example.httpreading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.httpreading.api.BusinessException;
import com.example.httpreading.domain.entity.Books;

class BookFileDownloadServiceTest {

    private BooksService booksService;
    private DocumentStorageService storageService;
    private BookFileDownloadService service;

    @BeforeEach
    void setUp() {
        booksService = mock(BooksService.class);
        storageService = mock(DocumentStorageService.class);
        service = new BookFileDownloadService(booksService, storageService);
    }

    @Test
    void buildsDownloadMetadataFromStoredBookSource() {
        Books book = book(1L, "books/1/source/original.epub", "阅读 测试.epub");
        book.setSourceHash("abc123");
        Path source = Path.of("/tmp/original.epub");
        when(booksService.getBooks(1L)).thenReturn(book);
        when(storageService.readBookSource(1L, book.getSourceFilePath()))
            .thenReturn(new DocumentStorageService.StoredSourceFile(
                source, "application/epub+zip", 42));

        BookFileDownloadService.BookFileDownload download = service.getDownload(1L);

        assertThat(download.path()).isEqualTo(source);
        assertThat(download.mediaType()).isEqualTo("application/epub+zip");
        assertThat(download.filename()).isEqualTo("阅读 测试.epub");
        assertThat(download.contentLength()).isEqualTo(42);
        assertThat(download.entityTag()).isEqualTo("\"abc123\"");
    }

    @Test
    void calculatesMissingHashAndFallsBackToSafeFilename() {
        Books book = book(7L, "books/7/source/original.pdf", "../");
        Path source = Path.of("/tmp/original.pdf");
        when(booksService.getBooks(7L)).thenReturn(book);
        when(storageService.readBookSource(7L, book.getSourceFilePath()))
            .thenReturn(new DocumentStorageService.StoredSourceFile(source, "application/pdf", 10));
        when(storageService.sha256(book.getSourceFilePath())).thenReturn("fallback-hash");
        when(storageService.extension("original.pdf")).thenReturn(".pdf");

        BookFileDownloadService.BookFileDownload download = service.getDownload(7L);

        assertThat(download.filename()).isEqualTo("book-7.pdf");
        assertThat(download.entityTag()).isEqualTo("\"fallback-hash\"");
        verify(storageService).sha256(book.getSourceFilePath());
    }

    @Test
    void rejectsBooksWithoutStoredSources() {
        when(booksService.getBooks(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getDownload(99L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("书籍不存在");

        Books book = book(2L, null, null);
        when(booksService.getBooks(2L)).thenReturn(book);
        assertThatThrownBy(() -> service.getDownload(2L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("没有可下载");
    }

    private Books book(Long id, String sourcePath, String originalName) {
        Books book = new Books();
        book.setId(id);
        book.setSourceFilePath(sourcePath);
        book.setSourceOriginalName(originalName);
        return book;
    }
}
