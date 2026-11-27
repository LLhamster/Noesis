package com.example.httpreading.modules.context.conversation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

import java.time.Instant;

@Entity
@Table(name = "reading_ai_conversation", indexes = @Index(columnList = "userId,bookId,usedAt"))
public class ReadingConversation {
    @Id
    String id;
    Long userId;
    Long bookId;
    String title;
    Instant usedAt;
    boolean defaultConversation;
}
