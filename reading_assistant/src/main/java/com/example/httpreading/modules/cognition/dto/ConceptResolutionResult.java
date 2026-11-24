package com.example.httpreading.modules.cognition.dto;

import java.util.List;

import com.example.httpreading.modules.cognition.domain.ConceptResolutionDecision;
import com.example.httpreading.modules.cognition.domain.ConfidenceLevel;

public record ConceptResolutionResult(
    String eventId,
    String primaryConceptName,
    Long matchedConceptId,
    List<ConceptCandidateDto> candidateConcepts,
    double confidence,
    ConfidenceLevel confidenceLevel,
    ConceptResolutionDecision decision,
    ScoreBreakdownDto scoreBreakdown,
    List<String> contextEvidence,
    String reason
) {
}
