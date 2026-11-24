package com.example.httpreading.modules.cognition.repository;

import java.util.List;
import java.util.Optional;

import com.example.httpreading.modules.cognition.domain.ConceptAlias;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConceptAliasRepository extends JpaRepository<ConceptAlias, Long> {
    Optional<ConceptAlias> findFirstByAliasNameIgnoreCase(String aliasName);

    List<ConceptAlias> findByNormalizedAliasName(String normalizedAliasName);

    boolean existsByConceptIdAndNormalizedAliasName(Long conceptId, String normalizedAliasName);
}
