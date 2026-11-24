package com.example.httpreading.service;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.stereotype.Service;

import com.example.httpreading.api.ErrorCode;
import com.example.httpreading.domain.entity.Books;

@Service
public class BookFileDownloadService {

    private final BooksService booksService;
    private final DocumentStorageService documentStorageService;

    public BookFileDownloadService(BooksService booksService,
                                   DocumentStorageService documentStorageService) {
        this.booksService = booksService;
        this.documentStorageService = documentStorageService;
    }

    public BookFileDownload getDownload(Long bookId) {
        Books book = booksService.getBooks(bookId);
        if (book == null) {
            throw ErrorCode.BOOK_NOT_FOUND.toException();
        }
        if (book.getSourceFilePath() == null || book.getSourceFilePath().isBlank()) {
            throw ErrorCode.RESOURCE_NOT_FOUND.toException("该书没有可下载的原始文件");
        }

        DocumentStorageService.StoredSourceFile source =
            documentStorageService.readBookSource(bookId, book.getSourceFilePath());
        String sourceHash = book.getSourceHash();
        if (sourceHash == null || sourceHash.isBlank()) {
            sourceHash = documentStorageService.sha256(book.getSourceFilePath());
        }

        return new BookFileDownload(
            source.path(),
            source.mediaType(),
            downloadFilename(book, source.path()),
            source.contentLength(),
            quoteEntityTag(sourceHash));
    }

    private String downloadFilename(Books book, Path sourcePath) {
        String originalName = safeFilename(book.getSourceOriginalName());
        if (originalName != null) {
            return originalName;
        }
        String extension = documentStorageService.extension(sourcePath.getFileName().toString());
        return "book-" + book.getId() + extension;
    }

    private String safeFilename(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            String filename = Path.of(candidate).getFileName().toString()
                .replace('\r', '_')
                .replace('\n', '_');
            return filename.isBlank() || ".".equals(filename) || "..".equals(filename)
                ? null
                : filename;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String quoteEntityTag(String value) {
        String normalized = value.replace("\\", "").replace("\"", "").trim();
        if (normalized.isEmpty()) {
            throw ErrorCode.INTERNAL_ERROR.toException("无法生成原始书籍文件标识");
        }
        return '"' + normalized + '"';
    }

    public record BookFileDownload(
        Path path,
        String mediaType,
        String filename,
        long contentLength,
        String entityTag
    ) {
    }
}
