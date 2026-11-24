package com.example.httpreading.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.example.httpreading.domain.entity.CloudBookImport;

public interface CloudBookImportRepository extends JpaRepository<CloudBookImport, UUID> {
    Optional<CloudBookImport> findByWorkKey(String workKey);
    Optional<CloudBookImport> findBySourceHash(String sourceHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from CloudBookImport entry where entry.id = :id and entry.userId = :userId")
    Optional<CloudBookImport> findForCompletion(UUID id, Long userId);
}
