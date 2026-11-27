package com.example.httpreading.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.httpreading.api.AiChatStreamingController;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.modules.context.conversation.ReadingConversationService;
import com.example.httpreading.service.AiChatService;
import com.example.httpreading.service.AiChatRateLimiter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import okhttp3.Call;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

@ExtendWith(MockitoExtension.class)
class ReadingChatStreamServiceTest {
    @Mock private ReadingConversationService conversations;
    @Mock private AiChatService deep;
    @Mock private QuickReadingAnswerService quick;
    @Mock private AsyncTaskExecutor executor;
    @Mock private Call call;
    @Mock private AiChatRateLimiter rateLimiter;

    @Test
    void stopCancelsUpstreamPersistsPartialAndRejectsLateTokens() {
        var service = new ReadingChatStreamService(conversations, deep, quick, executor);
        UUID id = UUID.randomUUID();
        AiChatRequest request = request("quick");
        when(conversations.startStream(42L, request, id.toString())).thenReturn(7L);
        runInline();
        when(quick.answer(eq(request), any(ChatStream.class))).thenAnswer(invocation -> {
            ChatStream stream = invocation.getArgument(1);
            stream.attach(call);
            stream.delta("保留原文");
            service.stop(99L, id);
            stream.delta("与第二段");
            service.stop(42L, id);
            assertThrows(CancellationException.class, () -> stream.delta("迟到文本"));
            return new AiChatResponse("迟到终态", List.of());
        });

        service.start(42L, id, request);

        verify(call).cancel();
        var response = ArgumentCaptor.forClass(AiChatResponse.class);
        verify(conversations).finishStream(eq(7L), response.capture());
        assertEquals("stopped", response.getValue().getStatus());
        assertEquals("保留原文与第二段", response.getValue().getAnswer());
    }

    @Test
    void completedDeepGenerationPersistsTheRevisedAnswer() {
        var service = new ReadingChatStreamService(conversations, deep, quick, executor);
        UUID id = UUID.randomUUID();
        AiChatRequest request = request("deep");
        when(conversations.startStream(42L, request, id.toString())).thenReturn(8L);
        runInline();
        when(deep.streamChat(eq(request), any(ChatStream.class))).thenAnswer(invocation -> {
            ChatStream stream = invocation.getArgument(1);
            stream.stage("planning");
            stream.stage("answering");
            stream.delta("初稿");
            stream.stage("repairing");
            stream.reset();
            stream.delta("修订");
            return new AiChatResponse("修订后的回答", List.of("第 2 章"));
        });

        service.start(42L, id, request);

        var response = ArgumentCaptor.forClass(AiChatResponse.class);
        verify(conversations).finishStream(eq(8L), response.capture());
        assertEquals("completed", response.getValue().getStatus());
        assertEquals("修订后的回答", response.getValue().getAnswer());
    }

    @Test
    void mvcStreamsJsonEventsWithTheRequestIdAndPersistedTurnId() throws Exception {
        var service = new ReadingChatStreamService(conversations, deep, quick, executor);
        UUID id = UUID.randomUUID();
        runInline();
        when(rateLimiter.tryAcquire("42")).thenReturn(true);
        when(conversations.startStream(eq(42L), any(), eq(id.toString()))).thenReturn(10L);
        when(quick.answer(any(), any(ChatStream.class))).thenAnswer(invocation -> {
            ChatStream stream = invocation.getArgument(1);
            stream.stage("answering");
            stream.delta("内容");
            return new AiChatResponse("内容", List.of());
        });

        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new AiChatStreamingController(service, rateLimiter)).build();
        var started = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/ai/chat/streams/{id}", id)
                .requestAttr("userId", 42L)
                .contentType("application/json")
                .accept("text/event-stream")
                .content("""
                    {"conversationId":"conversation","mode":"quick","bookId":1,"chapterIndex":2,"question":"test"}
                    """))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
            .andReturn();
        var completed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .asyncDispatch(started)).andReturn().getResponse();
        assertEquals(200, completed.getStatus());
        assertTrue(completed.getContentType().startsWith("text/event-stream"));
        String wire = completed.getContentAsString();
        assertTrue(wire.contains("data:{\"requestId\":\"" + id + "\""));
        assertTrue(wire.contains("\"type\":\"delta\""));
        assertTrue(wire.contains("\"conversationTurnId\":10"));
    }

    private void runInline() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
    }

    private static AiChatRequest request(String mode) {
        var request = new AiChatRequest();
        request.setConversationId("conversation");
        request.setBookId(1L);
        request.setChapterIndex(2);
        request.setQuestion("继续解释");
        request.setMode(mode);
        return request;
    }
}
