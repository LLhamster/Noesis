package com.example.httpreading.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReadingAnnotationDtos {
    private ReadingAnnotationDtos() {}

    public record CreateRequest(
        @NotBlank @Size(max = 5000) String selectedText,
        @NotNull @Min(0) @Max(10_000_000) Integer startOffset,
        @NotNull @Min(1) @Max(10_000_000) Integer endOffset,
        @Size(max = 300) String prefixText,
        @Size(max = 300) String suffixText,
        @NotBlank @Size(max = 16) String color,
        @Size(max = 16) String markStyle,
        @Size(max = 10_000) String noteContent,
        @Size(max = 1000) String cfi
    ) {
        public CreateRequest(
            String selectedText,
            Integer startOffset,
            Integer endOffset,
            String prefixText,
            String suffixText,
            String color,
            String markStyle,
            String noteContent
        ) {
            this(selectedText, startOffset, endOffset, prefixText, suffixText, color, markStyle, noteContent, null);
        }
    }

    public record UpdateRequest(
        @Size(max = 16) String color,
        @Size(max = 16) String markStyle,
        @Size(max = 10_000) String noteContent
    ) {}

    public record Response(
        Long id,
        Long bookId,
        Integer chapterIndex,
        String chapterTitle,
        String selectedText,
        Integer startOffset,
        Integer endOffset,
        String prefixText,
        String suffixText,
        String color,
        String markStyle,
        String noteContent,
        String cfi,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {}
}
