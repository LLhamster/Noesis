package com.example.httpreading.modules.cognition.repository;

import java.util.List;

import com.example.httpreading.modules.cognition.domain.ConceptSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptSourceRepository extends JpaRepository<ConceptSource, Long> {
    List<ConceptSource> findByConceptIdOrderByCreatedAtDesc(Long conceptId);
}
