package com.example.httpreading.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.httpreading.domain.entity.Chapters;
import com.example.httpreading.repository.BooksRepository;
import com.example.httpreading.repository.ChaptersRepository;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Service
public class CloudBookIndexService {
    private final BooksRepository books;
    private final ChaptersRepository chapters;
    private final RagService rag;

    public CloudBookIndexService(BooksRepository books, ChaptersRepository chapters, RagService rag) {
        this.books = books;
        this.chapters = chapters;
        this.rag = rag;
    }

    @Transactional
    public void prepare(Long bookId, Long userId, List<Section> sections) {
        var book = books.findOwnedCloudBookForUpdate(bookId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cloud book not found"));
        if ("READY".equals(book.getParseStatus())) {
            return;
        }

        var indices = new HashSet<Integer>();
        long totalCharacters = 0;
        for (Section section : sections) {
            totalCharacters += section.text().length();
            if (!indices.add(section.index()) || totalCharacters > 8_000_000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cloud chapter payload");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Chapters> entities = sections.stream()
                .sorted(java.util.Comparator.comparingInt(Section::index))
                .map(section -> {
                    Chapters chapter = new Chapters();
                    chapter.setBookId(bookId);
                    chapter.setChapterIndex(section.index());
                    chapter.setTitle(section.title());
                    chapter.setContent(section.text());
                    chapter.setCreatedAt(now);
                    chapter.setUpdatedAt(now);
                    return chapter;
                }).toList();
        chapters.saveAllAndFlush(entities);
        if (rag.rebuildIndexForBook(bookId, entities, book.getTitle()) == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No searchable text found");
        }
        book.setParseStatus("READY");
    }

    public record Section(@Positive @Max(5000) int index,
                          @NotBlank @Size(max = 255) String title,
                          @NotBlank @Size(max = 500_000) String text) {}
}
