package com.example.httpreading.modules.cognition.service;

import java.util.List;

record ConceptModelAnalysis(
    String concept,
    double modelScore,
    double contextScore,
    List<String> evidence,
    String reason,
    String modelName,
    String promptVersion,
    String analyzerVersion
) {
}
