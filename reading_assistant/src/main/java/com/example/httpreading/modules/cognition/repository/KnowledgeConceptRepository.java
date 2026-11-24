package com.example.httpreading.modules.cognition.repository;

import java.util.List;
import java.util.Optional;

import com.example.httpreading.modules.cognition.domain.ConceptStatus;
import com.example.httpreading.modules.cognition.domain.KnowledgeConcept;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeConceptRepository extends JpaRepository<KnowledgeConcept, Long> {
    Optional<KnowledgeConcept> findFirstByCanonicalNameIgnoreCase(String canonicalName);

    List<KnowledgeConcept> findByNormalizedName(String normalizedName);

    List<KnowledgeConcept> findTop10ByNormalizedNameStartingWithOrNormalizedNameEndingWith(String prefix, String suffix);

    List<KnowledgeConcept> findByBookIdAndStatus(Long bookId, ConceptStatus status);
}
