package com.example.httpreading.modules.cognition.dto;

public record ScoreBreakdownDto(
    double modelScore,
    double lexicalMatchScore,
    double contextSupportScore,
    double historyConsistencyScore,
    double candidateGapScore
) {
}
