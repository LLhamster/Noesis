package com.example.httpreading.domain.entity;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "cloud_book_import")
public class CloudBookImport implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String workKey;

    @Column(nullable = false, unique = true, length = 64)
    private String sourceHash;

    @Column(nullable = false)
    private Long userId;

    private Long bookId;

    @Transient
    private boolean newEntity = true;

    protected CloudBookImport() {}

    public CloudBookImport(UUID id, String workKey, String sourceHash, Long userId) {
        this.id = id;
        this.workKey = workKey;
        this.sourceHash = sourceHash;
        this.userId = userId;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return newEntity; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newEntity = false; }

    public String getWorkKey() { return workKey; }
    public String getSourceHash() { return sourceHash; }
    public Long getUserId() { return userId; }
    public Long getBookId() { return bookId; }
    public void complete(Long bookId) { this.bookId = bookId; }
}
