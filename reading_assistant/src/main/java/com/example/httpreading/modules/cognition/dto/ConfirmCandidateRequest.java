package com.example.httpreading.modules.cognition.dto;

public record ConfirmCandidateRequest(
    Long targetConceptId,
    String aliasName,
    String description,
    String reason
) {
}
