package com.example.httpreading.modules.cognition.dto;

import com.example.httpreading.modules.cognition.domain.ConceptCandidateStatus;
import com.example.httpreading.modules.cognition.domain.ConceptStatus;

public record ConceptManagementResponse(
    String status,
    Long conceptId,
    String conceptName,
    ConceptStatus conceptStatus,
    Long candidateRecordId,
    ConceptCandidateStatus candidateStatus
) {
}
