package com.example.httpreading.modules.context.conversation;

import com.example.httpreading.common.web.ErrorCode;
import com.example.httpreading.domain.entity.Books;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.repository.BooksRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingConversationService {
    public static final long STREAM_TIMEOUT_MS = 300_000L;
    private static final int PAGE_SIZE = 20;
    private static final int CONTEXT_CHARS = 12000;

    private final BooksRepository books;
    private final ReadingConversationRepository conversations;
    private final ReadingConversationTurnRepository turns;
    private final ObjectMapper json;

    public ReadingConversationService(BooksRepository books,
                                      ReadingConversationRepository conversations,
                                      ReadingConversationTurnRepository turns,
                                      ObjectMapper json) {
        this.books = books;
        this.conversations = conversations;
        this.turns = turns;
        this.json = json;
    }

    @Transactional
    public List<ConversationView> list(Long userId, Long bookId) {
        requireBook(books.findBookForConversationUpdate(bookId).orElse(null), userId);
        String key = "reading:" + userId + ":" + bookId;
        String defaultId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
        if (!conversations.existsById(defaultId)) {
            create(userId, bookId, defaultId, true);
        }
        return conversations.findByUserIdAndBookIdOrderByUsedAtDescIdDesc(userId, bookId)
            .stream().map(ReadingConversationService::view).toList();
    }

    @Transactional
    public ConversationView create(Long userId, Long bookId) {
        requireBook(books.findById(bookId).orElse(null), userId);
        return view(create(userId, bookId, UUID.randomUUID().toString(), false));
    }

    @Transactional
    public HistoryPage open(Long userId, Long bookId, String id, Long before) {
        ReadingConversation conversation = requireConversation(userId, bookId, id);
        if (before == null) conversation.usedAt = Instant.now();
        var records = turns.findByConversationIdAndIdLessThanOrderByIdDesc(id,
            before == null ? Long.MAX_VALUE : before, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = records.size() > PAGE_SIZE;
        List<TurnView> page = new ArrayList<>(records.stream().limit(PAGE_SIZE).map(this::viewTurn).toList());
        Collections.reverse(page);
        return new HistoryPage(page, hasMore ? page.get(0).id() : null);
    }

    @Transactional(readOnly = true)
    public String recentDialogue(Long userId, Long bookId, String id) {
        requireConversation(userId, bookId, id);
        List<String> entries = new ArrayList<>();
        int remaining = CONTEXT_CHARS;
        for (ReadingConversationTurn turn : turns.findTop8ByConversationIdAndStatusOrderByIdDesc(id, "completed")) {
            String entry = "读者：" + turn.question + "\n助手：" + turn.answer;
            if (entry.length() + 2 > remaining) break;
            entries.add(entry);
            remaining -= entry.length() + 2;
        }
        Collections.reverse(entries);
        return String.join("\n\n", entries);
    }

    @Transactional
    public Long saveTurn(Long userId, AiChatRequest request, AiChatResponse response) {
        ReadingConversation conversation = requireConversation(userId, request.getBookId(), request.getConversationId());
        ReadingConversationTurn turn = createTurn(conversation, request);
        applyResponse(turn, response);
        return turns.save(turn).id;
    }

    @Transactional
    public Long startStream(Long userId, AiChatRequest request, String requestId) {
        ReadingConversation conversation = requireConversation(userId, request.getBookId(), request.getConversationId());
        ReadingConversationTurn turn = createTurn(conversation, request);
        turn.requestId = requestId;
        applyResponse(turn, new AiChatResponse("", List.of()));
        turn.status = "generating";
        turn.streamDeadline = Instant.now().plusMillis(STREAM_TIMEOUT_MS + 5000);
        return turns.save(turn).id;
    }

    @Transactional
    public void finishStream(Long turnId, AiChatResponse response) {
        ReadingConversationTurn turn = turns.findById(turnId).orElseThrow();
        if ("generating".equals(turn.status)) applyResponse(turn, response);
    }

    private static ReadingConversationTurn createTurn(ReadingConversation conversation, AiChatRequest request) {
        ReadingConversationTurn turn = new ReadingConversationTurn();
        turn.conversationId = conversation.id;
        turn.chapterIndex = request.getChapterIndex();
        turn.question = request.getQuestion();
        turn.selectedText = request.getSelectedText();
        turn.mode = "quick".equals(request.getMode()) ? "quick" : "deep";
        if (conversation.title == null) {
            String question = request.getQuestion().strip();
            int length = Math.min(40, question.codePointCount(0, question.length()));
            conversation.title = question.substring(0, question.offsetByCodePoints(0, length));
        }
        return turn;
    }

    private void applyResponse(ReadingConversationTurn turn, AiChatResponse response) {
        turn.answer = response.getAnswer();
        turn.status = response.getStatus();
        try {
            turn.sourcesJson = json.writeValueAsString(response.getSources() == null ? List.of() : response.getSources());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot store answer sources", error);
        }
    }

    private ReadingConversation requireConversation(Long userId, Long bookId, String id) {
        requireBook(books.findById(bookId).orElse(null), userId);
        return conversations.findByIdAndUserIdAndBookId(id, userId, bookId)
            .orElseThrow(() -> ErrorCode.RESOURCE_NOT_FOUND.toException());
    }

    private static void requireBook(Books book, Long userId) {
        if (book == null || (book.getCloudOwnerId() != null && !book.getCloudOwnerId().equals(userId))) {
            throw ErrorCode.BOOK_NOT_FOUND.toException();
        }
    }

    private ReadingConversation create(Long userId, Long bookId, String id, boolean isDefault) {
        ReadingConversation conversation = new ReadingConversation();
        conversation.id = id;
        conversation.userId = userId;
        conversation.bookId = bookId;
        conversation.usedAt = Instant.now();
        conversation.defaultConversation = isDefault;
        return conversations.save(conversation);
    }

    private static ConversationView view(ReadingConversation conversation) {
        return new ConversationView(conversation.id, conversation.title, conversation.defaultConversation);
    }

    private TurnView viewTurn(ReadingConversationTurn turn) {
        try {
            List<String> sources = json.readValue(turn.sourcesJson, new TypeReference<>() {});
            String status = turn.status;
            if ("generating".equals(status) && turn.streamDeadline != null
                && Instant.now().isAfter(turn.streamDeadline)) {
                status = "interrupted";
            }
            return new TurnView(turn.id, turn.chapterIndex, turn.question, turn.selectedText, turn.answer,
                sources, status, turn.mode == null ? "deep" : turn.mode, turn.requestId);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read answer sources", error);
        }
    }

    public record ConversationView(String id, String title, boolean defaultConversation) {}
    public record TurnView(Long id, Integer chapterIndex, String question, String selectedText,
                           String answer, List<String> sources, String status, String mode, String requestId) {}
    public record HistoryPage(List<TurnView> turns, Long before) {}
}
