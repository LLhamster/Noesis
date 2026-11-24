package com.example.httpreading.modules.cognition.repository;

import java.util.Optional;

import com.example.httpreading.modules.cognition.domain.ConceptMergeRelation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptMergeRelationRepository extends JpaRepository<ConceptMergeRelation, Long> {
    Optional<ConceptMergeRelation> findFirstBySourceConceptIdOrderByCreatedAtDesc(Long sourceConceptId);
}
