package com.example.httpreading.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.httpreading.api.CommonResponse;
import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.service.BookMetadataNormalizer;
import com.example.httpreading.service.BooksService;
import com.example.httpreading.service.CloudBookImportService;
import com.example.httpreading.service.CloudBookIndexService;
import com.example.httpreading.service.CloudBookIndexService.Section;
import com.example.httpreading.security.JwtAuthFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/client/books")
public class ClientLibraryController {
    private final BooksService booksService;
    private final CloudBookImportService cloudImports;
    private final CloudBookIndexService cloudIndex;

    public ClientLibraryController(BooksService booksService, CloudBookImportService cloudImports,
            CloudBookIndexService cloudIndex) {
        this.booksService = booksService;
        this.cloudImports = cloudImports;
        this.cloudIndex = cloudIndex;
    }

    @PostMapping("/{bookId}/index")
    public CommonResponse<IndexStatus> index(
            @RequestAttribute(JwtAuthFilter.ATTR_USER_ID) Long userId,
            @PathVariable @Positive Long bookId,
            @Valid @RequestBody IndexBook input) {
        cloudIndex.prepare(bookId, userId, input.sections());
        return CommonResponse.success(new IndexStatus(true));
    }

    @PostMapping("/imports/{importId}/complete")
    public CommonResponse<CompletedImport> complete(
            @RequestAttribute(JwtAuthFilter.ATTR_USER_ID) Long userId,
            @PathVariable UUID importId,
            @Valid @RequestBody CompleteImport input) {
        return CommonResponse.success(new CompletedImport(
                cloudImports.complete(userId, importId, input.title(), input.author(), input.cloudDrivePath())));
    }

    @PostMapping("/imports/prepare")
    public CommonResponse<CloudBookImportService.Decision> prepare(
            @RequestAttribute(JwtAuthFilter.ATTR_USER_ID) Long userId,
            @Valid @RequestBody PrepareImport input) {
        return CommonResponse.success(cloudImports.prepare(
                userId, input.requestId(), input.title(), input.author(), input.sha256()));
    }

    @PostMapping("/metadata/normalize")
    public CommonResponse<NormalizedMetadata> normalize(@Valid @RequestBody BookMetadata input) {
        return CommonResponse.success(new NormalizedMetadata(input,
                BookMetadataNormalizer.titleKey(input.title()),
                BookMetadataNormalizer.authorKey(input.author())));
    }

    @GetMapping
    public CommonResponse<BookPage> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestAttribute(JwtAuthFilter.ATTR_USER_ID) Long userId) {
        Page<Books> result = booksService.requestBooks(null, null, page, 20, "updatedAt", "desc");
        List<BookSummary> books = result.getContent().stream()
            .filter(book -> (book.getSourceFilePath() != null
                    && ("epub".equalsIgnoreCase(book.getSourceFormat())
                        || "pdf".equalsIgnoreCase(book.getSourceFormat())))
                || (book.getCloudDrivePath() != null && userId.equals(book.getCloudOwnerId())))
            .map(book -> new BookSummary(book.getId(), book.getTitle(), book.getAuthor(),
                book.getSourceFormat().toLowerCase(java.util.Locale.ROOT), book.getCloudDrivePath(),
                book.getCloudDrivePath() != null && "READY".equals(book.getParseStatus())))
            .toList();
        return CommonResponse.success(new BookPage(books, result.hasNext()));
    }

    public record BookSummary(Long id, String title, String author, String format, String cloudDrivePath,
                              boolean indexReady) {}
    public record BookPage(List<BookSummary> books, boolean hasNext) {}
    public record BookMetadata(
            @Size(max = 255) String title,
            @Size(max = 255) String author,
            @Size(max = 32) String isbn,
            @Size(max = 32) String language,
            @Size(max = 255) String publisher,
            @Size(max = 64) String edition) {}
    public record NormalizedMetadata(BookMetadata original, String titleKey, String authorKey) {}
    public record PrepareImport(
            @NotNull UUID requestId,
            @Size(max = 255) String title,
            @Size(max = 255) String author,
            @NotNull @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256) {}
    public record CompleteImport(@NotBlank @Size(max = 255) String title,
                                 @NotBlank @Size(max = 255) String author,
                                 @Size(max = 512) String cloudDrivePath) {}
    public record CompletedImport(Long bookId) {}
    public record IndexBook(@NotNull @Size(min = 1, max = 5000)
                            List<@NotNull @Valid Section> sections) {}
    public record IndexStatus(boolean indexReady) {}
}
