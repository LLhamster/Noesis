package com.example.httpreading.modules.cognition.service;

import java.util.List;

import com.example.httpreading.modules.cognition.dto.ConceptCandidateDto;

public interface ConceptSimilarityRecallPort {
    List<ConceptCandidateDto> recall(String candidateName, Long bookId, int limit);
}
