package com.example.httpreading.modules.cognition.service;

import java.util.List;

import com.example.httpreading.modules.cognition.dto.ConceptCandidateDto;
import org.springframework.stereotype.Component;

@Component
public class NoopConceptSimilarityRecallPort implements ConceptSimilarityRecallPort {
    @Override
    public List<ConceptCandidateDto> recall(String candidateName, Long bookId, int limit) {
        return List.of();
    }
}
