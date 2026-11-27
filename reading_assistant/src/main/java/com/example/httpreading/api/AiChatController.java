package com.example.httpreading.api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.httpreading.common.web.CommonResponse;
import com.example.httpreading.common.web.ErrorCode;
import com.example.httpreading.common.security.JwtAuthFilter;
import com.example.httpreading.common.web.BusinessException;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.modules.context.conversation.ReadingConversationService;
import com.example.httpreading.modules.context.conversation.ReadingConversationService.ConversationView;
import com.example.httpreading.modules.context.conversation.ReadingConversationService.HistoryPage;
import com.example.httpreading.service.AiChatRateLimiter;
import com.example.httpreading.service.AiChatService;
import com.example.httpreading.service.ai.QuickReadingAnswerService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);

    private final AiChatService aiChatService;
    private final AiChatRateLimiter rateLimiter;

    private final ReadingConversationService conversations;
    private final QuickReadingAnswerService quickAnswers;

    public AiChatController(AiChatService aiChatService, AiChatRateLimiter rateLimiter,
                            ReadingConversationService conversations, QuickReadingAnswerService quickAnswers) {
        this.aiChatService = aiChatService;
        this.rateLimiter = rateLimiter;
        this.conversations = conversations;
        this.quickAnswers = quickAnswers;
    }

    @PostMapping({"", "/chat"})
    public CommonResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest body, HttpServletRequest request) {
        Long userId = currentUserId(request);
        body.setUserId(String.valueOf(userId));
        if (!rateLimiter.tryAcquire(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        log.info("AI问答请求 - bookId:{}, chapterIndex:{}, question:{}",
                body.getBookId(), body.getChapterIndex(), body.getQuestion());

        if (body.getConversationId() != null) {
            body.setRecentConversationDialogue(
                conversations.recentDialogue(userId, body.getBookId(), body.getConversationId()));
            body.setSessionId("reading:" + body.getConversationId());
            body.setContextId(null);
        } else if (body.getSessionId() != null && body.getSessionId().startsWith("reading:")) {
            throw ErrorCode.RESOURCE_NOT_FOUND.toException();
        }
        boolean quick = "quick".equals(body.getMode());
        if (quick && body.getConversationId() == null) {
            throw ErrorCode.BAD_REQUEST.toException("快速模式需要阅读会话");
        }
        AiChatResponse response = quick ? quickAnswers.answer(body) : aiChatService.chat(body);
        if (body.getConversationId() != null) {
            response.setConversationTurnId(conversations.saveTurn(userId, body, response));
        }

        log.info("AI问答完成 - bookId:{}, contextId:{}, answer长度:{} 字, 来源数:{}",
                body.getBookId(),
                response.getContextId(),
                response.getAnswer() == null ? 0 : response.getAnswer().length(),
                response.getSources() == null ? 0 : response.getSources().size());
        return CommonResponse.success(response);
    }

    @GetMapping
    public CommonResponse<AiChatResponse> getAnswer(@Valid @RequestBody AiChatRequest body, HttpServletRequest request) {
        return chat(body, request);
    }

    @GetMapping("/books/{bookId}/conversations")
    public CommonResponse<List<ConversationView>> listConversations(@PathVariable Long bookId,
                                                                    HttpServletRequest request) {
        return CommonResponse.success(conversations.list(currentUserId(request), bookId));
    }

    @PostMapping("/books/{bookId}/conversations")
    public CommonResponse<ConversationView> createConversation(@PathVariable Long bookId,
                                                                HttpServletRequest request) {
        return CommonResponse.success(conversations.create(currentUserId(request), bookId));
    }

    @GetMapping("/books/{bookId}/conversations/{id}/turns")
    public CommonResponse<HistoryPage> conversationHistory(@PathVariable Long bookId,
                                                           @PathVariable String id,
                                                           @RequestParam(required = false) Long before,
                                                           HttpServletRequest request) {
        return CommonResponse.success(conversations.open(currentUserId(request), bookId, id, before));
    }

    private Long currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (value instanceof Long id) {
            return id;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
