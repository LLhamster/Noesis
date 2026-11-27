package com.example.httpreading.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import com.example.httpreading.domain.entity.Books;

public interface BooksRepository extends JpaRepository<Books, Long> {
    public Page<Books> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
        String titleKey,
        String authorKey,
        Pageable pageable
    );
    public Page<Books> findByStatus(String status, Pageable pageable);

    Optional<Books> findBySourceHash(String sourceHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select book from Books book where book.id = :id")
    Optional<Books> findBookForConversationUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select book from Books book where book.id = :id and book.cloudOwnerId = :userId")
    Optional<Books> findOwnedCloudBookForUpdate(Long id, Long userId);
    
}
