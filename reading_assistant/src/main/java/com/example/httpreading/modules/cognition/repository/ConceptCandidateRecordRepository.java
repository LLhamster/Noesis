package com.example.httpreading.modules.cognition.repository;

import java.util.List;

import com.example.httpreading.modules.cognition.domain.ConceptCandidateRecord;
import com.example.httpreading.modules.cognition.domain.ConceptCandidateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptCandidateRecordRepository extends JpaRepository<ConceptCandidateRecord, Long> {
    List<ConceptCandidateRecord> findByStatusOrderByCreatedAtDesc(ConceptCandidateStatus status);

    List<ConceptCandidateRecord> findByEventIdOrderByCreatedAtDesc(String eventId);
}
