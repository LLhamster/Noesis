package com.example.httpreading.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.httpreading.common.web.GlobalExceptionHandler;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.modules.context.conversation.ReadingConversationService;
import com.example.httpreading.service.AiChatRateLimiter;
import com.example.httpreading.service.AiChatService;
import com.example.httpreading.service.ai.QuickReadingAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {
    @Mock
    private AiChatService aiChatService;

    @Mock
    private AiChatRateLimiter rateLimiter;

    @Mock
    private ReadingConversationService conversations;

    @Mock
    private QuickReadingAnswerService quickAnswers;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new AiChatController(aiChatService, rateLimiter, conversations, quickAnswers))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void usesAuthenticatedUserIdForChatRequest() throws Exception {
        when(rateLimiter.tryAcquire("42")).thenReturn(true);
        when(aiChatService.chat(any())).thenReturn(new AiChatResponse("回答", java.util.List.of("source")));

        mockMvc.perform(post("/api/ai/chat")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookId":1,"chapterIndex":1,"question":"解释这一段","userId":"spoofed"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value("回答"));

        ArgumentCaptor<com.example.httpreading.dto.AiChatRequest> captor =
            ArgumentCaptor.forClass(com.example.httpreading.dto.AiChatRequest.class);
        verify(aiChatService).chat(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("42", captor.getValue().getUserId());
    }

    @Test
    void managedConversationUsesStoredDialogueAndReturnsPersistedTurn() throws Exception {
        when(rateLimiter.tryAcquire("42")).thenReturn(true);
        when(conversations.recentDialogue(42L, 1L, "conversation")).thenReturn("上轮问答");
        when(conversations.saveTurn(org.mockito.ArgumentMatchers.eq(42L), any(), any())).thenReturn(8L);
        when(aiChatService.chat(any())).thenReturn(new AiChatResponse("回答", java.util.List.of()));

        mockMvc.perform(post("/api/ai/chat")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookId":1,"chapterIndex":2,"question":"追问","conversationId":"conversation",
                     "sessionId":"spoofed","recentConversationDialogue":"injected"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.conversationTurnId").value(8));

        var captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatService).chat(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("reading:conversation", captor.getValue().getSessionId());
        org.junit.jupiter.api.Assertions.assertEquals("上轮问答", captor.getValue().getRecentConversationDialogue());
    }

    @Test
    void quickModeUsesOnlyTheQuickPipelineAndSavesTheTurn() throws Exception {
        when(rateLimiter.tryAcquire("42")).thenReturn(true);
        when(conversations.recentDialogue(42L, 1L, "conversation")).thenReturn("上轮问答");
        when(quickAnswers.answer(any())).thenReturn(new AiChatResponse("快速回答", java.util.List.of()));
        when(conversations.saveTurn(org.mockito.ArgumentMatchers.eq(42L), any(), any())).thenReturn(8L);

        mockMvc.perform(post("/api/ai/chat")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookId":1,"chapterIndex":2,"question":"追问","conversationId":"conversation","mode":"quick"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.answer").value("快速回答"))
            .andExpect(jsonPath("$.data.conversationTurnId").value(8));

        var captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(quickAnswers).answer(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("上轮问答", captor.getValue().getRecentConversationDialogue());
        org.mockito.Mockito.verifyNoInteractions(aiChatService);
    }

    @Test
    void rejectsChatWhenRateLimited() throws Exception {
        when(rateLimiter.tryAcquire("42")).thenReturn(false);

        mockMvc.perform(post("/api/ai/chat")
                .requestAttr("userId", 42L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookId":1,"chapterIndex":1,"question":"解释这一段"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    void rejectsChatWithoutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"bookId":1,"chapterIndex":1,"question":"解释这一段"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(40101));
    }
}
