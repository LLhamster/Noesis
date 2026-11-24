package com.example.httpreading.modules.profile.repository;

import java.util.Optional;

import com.example.httpreading.modules.profile.domain.ProfileVectorIndexMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileVectorIndexMappingRepository extends JpaRepository<ProfileVectorIndexMapping, Long> {
    Optional<ProfileVectorIndexMapping> findBySourceTableAndSourceId(String sourceTable, Long sourceId);

    void deleteBySourceTableAndSourceId(String sourceTable, Long sourceId);
}
