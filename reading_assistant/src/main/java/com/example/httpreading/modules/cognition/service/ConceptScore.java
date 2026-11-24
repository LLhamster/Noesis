package com.example.httpreading.modules.cognition.service;

import java.util.List;

import com.example.httpreading.modules.cognition.domain.ConfidenceLevel;
import com.example.httpreading.modules.cognition.dto.ScoreBreakdownDto;

record ConceptScore(
    double confidence,
    ConfidenceLevel confidenceLevel,
    ScoreBreakdownDto breakdown,
    List<String> evidence
) {
}
