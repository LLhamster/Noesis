package com.example.httpreading.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.domain.entity.CloudBookImport;
import com.example.httpreading.repository.BooksRepository;
import com.example.httpreading.repository.CloudBookImportRepository;

@Service
public class CloudBookImportService {
    private final BooksRepository books;
    private final CloudBookImportRepository imports;

    public CloudBookImportService(BooksRepository books, CloudBookImportRepository imports) {
        this.books = books;
        this.imports = imports;
    }

    public Decision prepare(Long userId, UUID requestId, String title, String author, String sha256) {
        String hash = sha256.toLowerCase(Locale.ROOT);
        var sameFile = books.findBySourceHash(hash);
        if (sameFile.isPresent()) {
            return new Decision(Status.EXISTS, sameFile.get().getId(), null, List.of());
        }

        String titleKey = BookMetadataNormalizer.titleKey(title);
        String authorKey = BookMetadataNormalizer.authorKey(author);
        if (titleKey.isEmpty() || authorKey.isEmpty()) {
            return new Decision(Status.NEEDS_CONFIRMATION, null, null, List.of());
        }

        List<Books> candidates = books.findAll().stream()
                .filter(book -> titleKey.equals(BookMetadataNormalizer.titleKey(book.getTitle())))
                .toList();
        List<Books> matches = candidates.stream()
                .filter(book -> authorKey.equals(BookMetadataNormalizer.authorKey(book.getAuthor())))
                .toList();
        if (matches.size() == 1) {
            return new Decision(Status.EXISTS, matches.get(0).getId(), null, List.of());
        }
        if (matches.size() > 1 || candidates.stream().anyMatch(
                book -> BookMetadataNormalizer.authorKey(book.getAuthor()).isEmpty())) {
            return new Decision(Status.NEEDS_CONFIRMATION, null, null,
                    candidates.stream().map(book -> new Candidate(book.getId(), book.getTitle(), book.getAuthor()))
                            .toList());
        }

        String workKey = workKey(titleKey, authorKey);
        Decision previous = previousDecision(userId, requestId, workKey, hash);
        if (previous != null) {
            return previous;
        }
        try {
            imports.saveAndFlush(new CloudBookImport(requestId, workKey, hash, userId));
        } catch (DataIntegrityViolationException conflict) {
            Decision concurrent = previousDecision(userId, requestId, workKey, hash);
            if (concurrent != null) {
                return concurrent;
            }
            throw conflict;
        }
        return new Decision(Status.READY, null, requestId, List.of());
    }

    @Transactional
    public Long complete(Long userId, UUID requestId, String title, String author, String cloudDrivePath) {
        CloudBookImport reservation = imports.findForCompletion(requestId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import reservation not found"));
        String titleKey = BookMetadataNormalizer.titleKey(title);
        String authorKey = BookMetadataNormalizer.authorKey(author);
        if (!reservation.getWorkKey().equals(workKey(titleKey, authorKey))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Book metadata does not match reservation");
        }
        String legacyPath = "/Readest/noesis/" + requestId + ".epub";
        if (cloudDrivePath != null && (cloudDrivePath.indexOf('\\') >= 0
                || !cloudDrivePath.matches("/Readest/books/[0-9a-f]{32}/[^/\\p{Cntrl}]+[.]epub"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Readest book path");
        }
        if (reservation.getBookId() != null) {
            if (cloudDrivePath != null) {
                books.findById(reservation.getBookId())
                        .filter(book -> userId.equals(book.getCloudOwnerId()))
                        .filter(book -> legacyPath.equals(book.getCloudDrivePath()))
                        .ifPresent(book -> book.setCloudDrivePath(cloudDrivePath));
            }
            return reservation.getBookId();
        }
        Books book = new Books();
        book.setTitle(title.strip());
        book.setAuthor(author.strip());
        book.setSourceFormat("epub");
        book.setSourceHash(reservation.getSourceHash());
        book.setCloudDrivePath(cloudDrivePath == null ? legacyPath : cloudDrivePath);
        book.setCloudOwnerId(userId);
        LocalDateTime now = LocalDateTime.now();
        book.setCreatedAt(now);
        book.setUpdatedAt(now);
        Long bookId = books.saveAndFlush(book).getId();
        reservation.complete(bookId);
        return bookId;
    }

    private static String workKey(String titleKey, String authorKey) {
        return digest(titleKey.length() + ":" + titleKey + authorKey);
    }

    private Decision previousDecision(Long userId, UUID requestId, String workKey, String hash) {
        var byId = imports.findById(requestId);
        if (byId.isPresent()) {
            CloudBookImport reservation = byId.get();
            if (userId.equals(reservation.getUserId())
                    && workKey.equals(reservation.getWorkKey())
                    && hash.equals(reservation.getSourceHash())) {
                return new Decision(Status.READY, null, requestId, List.of());
            }
            return new Decision(Status.IN_PROGRESS, null, null, List.of());
        }
        if (imports.findByWorkKey(workKey).isPresent() || imports.findBySourceHash(hash).isPresent()) {
            return new Decision(Status.IN_PROGRESS, null, null, List.of());
        }
        return null;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public enum Status { EXISTS, NEEDS_CONFIRMATION, READY, IN_PROGRESS }

    public record Candidate(Long bookId, String title, String author) {}

    public record Decision(Status status, Long bookId, UUID importId, List<Candidate> candidates) {}
}
