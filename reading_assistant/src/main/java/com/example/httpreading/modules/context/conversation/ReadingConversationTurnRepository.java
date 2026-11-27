package com.example.httpreading.modules.context.conversation;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingConversationTurnRepository extends JpaRepository<ReadingConversationTurn, Long> {
    List<ReadingConversationTurn> findByConversationIdAndIdLessThanOrderByIdDesc(
        String conversationId, Long before, Pageable page);

    List<ReadingConversationTurn> findTop8ByConversationIdAndStatusOrderByIdDesc(
        String conversationId, String status);
}
