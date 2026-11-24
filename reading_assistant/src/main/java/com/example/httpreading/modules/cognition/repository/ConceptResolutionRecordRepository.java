package com.example.httpreading.modules.cognition.repository;

import java.util.Optional;

import com.example.httpreading.modules.cognition.domain.ConceptResolutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptResolutionRecordRepository extends JpaRepository<ConceptResolutionRecord, Long> {
    Optional<ConceptResolutionRecord> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
