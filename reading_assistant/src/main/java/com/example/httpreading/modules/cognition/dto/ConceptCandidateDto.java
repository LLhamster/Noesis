package com.example.httpreading.modules.cognition.dto;

public record ConceptCandidateDto(
    Long conceptId,
    String name,
    double score
) {
}
