package com.example.httpreading.modules.context.conversation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingConversationRepository extends JpaRepository<ReadingConversation, String> {
    List<ReadingConversation> findByUserIdAndBookIdOrderByUsedAtDescIdDesc(Long userId, Long bookId);
    Optional<ReadingConversation> findByIdAndUserIdAndBookId(String id, Long userId, Long bookId);
}
