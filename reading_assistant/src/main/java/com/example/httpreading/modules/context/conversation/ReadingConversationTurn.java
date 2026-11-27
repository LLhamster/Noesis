package com.example.httpreading.modules.context.conversation;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "reading_ai_turn", indexes = @Index(columnList = "conversationId,id"))
public class ReadingConversationTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String conversationId;
    @jakarta.persistence.Column(unique = true, length = 36)
    String requestId;
    java.time.Instant streamDeadline;
    Integer chapterIndex;
    @Lob
    String question;
    @Lob
    String selectedText;
    @Lob
    String answer;
    @Lob
    String sourcesJson;
    String status;
    String mode;
}
