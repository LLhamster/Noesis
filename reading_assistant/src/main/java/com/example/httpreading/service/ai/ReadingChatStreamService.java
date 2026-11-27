package com.example.httpreading.service.ai;

import com.example.httpreading.common.web.ErrorCode;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.modules.context.conversation.ReadingConversationService;
import com.example.httpreading.service.AiChatService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ReadingChatStreamService {
    private static final Logger log = LoggerFactory.getLogger(ReadingChatStreamService.class);

    private final ConcurrentHashMap<UUID, Generation> active = new ConcurrentHashMap<>();
    private final ReadingConversationService conversations;
    private final AiChatService deep;
    private final QuickReadingAnswerService quick;
    private final AsyncTaskExecutor executor;

    public ReadingChatStreamService(ReadingConversationService conversations, AiChatService deep,
                                    QuickReadingAnswerService quick,
                                    @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.conversations = conversations;
        this.deep = deep;
        this.quick = quick;
        this.executor = executor;
    }

    public SseEmitter start(Long userId, UUID id, AiChatRequest request) {
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            throw ErrorCode.BAD_REQUEST.toException("流式问答需要阅读会话");
        }
        request.setUserId(userId.toString());
        request.setRecentConversationDialogue(
            conversations.recentDialogue(userId, request.getBookId(), request.getConversationId()));
        request.setSessionId("reading:" + request.getConversationId());
        request.setContextId(null);

        Generation generation = new Generation(userId, id, "quick".equals(request.getMode()));
        if (active.putIfAbsent(id, generation) != null) {
            throw ErrorCode.BAD_REQUEST.toException("请求已存在");
        }
        try {
            generation.turnId = conversations.startStream(userId, request, id.toString());
        } catch (DataIntegrityViolationException error) {
            active.remove(id, generation);
            throw ErrorCode.BAD_REQUEST.toException("请求已存在");
        } catch (RuntimeException error) {
            active.remove(id, generation);
            throw error;
        }

        SseEmitter emitter = generation.emitter;
        emitter.onCompletion(() -> generation.end("interrupted"));
        emitter.onError(error -> generation.end("interrupted"));
        emitter.onTimeout(() -> generation.end("interrupted"));
        try {
            executor.execute(new DelegatingSecurityContextRunnable(() -> {
                try {
                    generation.stage("queued");
                    AiChatResponse response = generation.quick
                        ? quick.answer(request, generation) : deep.streamChat(request, generation);
                    generation.finish(response);
                } catch (CancellationException ignored) {
                    // Stop and disconnection callbacks persist their own terminal state.
                } catch (RuntimeException error) {
                    log.warn("Reading stream failed: requestId={}", id, error);
                    generation.end("failed");
                } finally {
                    active.remove(id, generation);
                }
            }));
        } catch (RuntimeException error) {
            active.remove(id, generation);
            generation.end("failed");
        }
        return emitter;
    }

    public void stop(Long userId, UUID id) {
        Generation generation = active.get(id);
        if (generation != null && generation.userId.equals(userId)) {
            generation.end("stopped");
        }
    }

    public record StreamEvent(UUID requestId, String type, String value, AiChatResponse result) {}

    private final class Generation implements ChatStream {
        private final Long userId;
        private final UUID id;
        private final boolean quick;
        private final SseEmitter emitter = new SseEmitter(ReadingConversationService.STREAM_TIMEOUT_MS);
        private final StringBuilder message = new StringBuilder();
        private final long started = System.nanoTime();
        private Long turnId;
        private long firstTokenMs = -1;
        private Call call;
        private boolean finished;
        private boolean resetPending;

        Generation(Long userId, UUID id, boolean quick) {
            this.userId = userId;
            this.id = id;
            this.quick = quick;
        }

        @Override
        public synchronized void stage(String stage) {
            checkActive();
            send(new StreamEvent(id, "stage", stage, null));
        }

        @Override
        public synchronized void delta(String text) {
            checkActive();
            if (resetPending) {
                resetPending = false;
                send(new StreamEvent(id, "reset", null, null));
                message.setLength(0);
            }
            if (firstTokenMs < 0) firstTokenMs = elapsedMs();
            message.append(text);
            send(new StreamEvent(id, "delta", text, null));
        }

        @Override
        public synchronized void reset() {
            checkActive();
            resetPending = true;
        }

        @Override
        public synchronized void checkActive() {
            if (finished) throw new CancellationException();
        }

        @Override
        public synchronized void attach(Call nextCall) {
            checkActive();
            call = nextCall;
        }

        @Override
        public synchronized void detach(Call completedCall) {
            if (call == completedCall) call = null;
        }

        synchronized void end(String status) {
            if (finished || turnId == null) return;
            if (call != null) call.cancel();
            AiChatResponse response = new AiChatResponse(message.toString(), List.of());
            response.setStatus(status);
            finish(response);
        }

        synchronized void finish(AiChatResponse response) {
            if (finished || turnId == null) return;
            finished = true;
            if ("model_unavailable".equals(response.getStatus()) && !message.isEmpty()) {
                response.setAnswer(message.toString());
            }
            try {
                conversations.finishStream(turnId, response);
                response.setConversationTurnId(turnId);
                emitter.send(SseEmitter.event().data(new StreamEvent(id, "terminal", null, response)));
                emitter.complete();
            } catch (IOException | IllegalStateException error) {
                emitter.completeWithError(error);
            } catch (RuntimeException error) {
                log.error("Could not persist reading stream: requestId={}", id, error);
                emitter.completeWithError(error);
            }
            log.info("Reading stream requestId={} mode={} status={} first_token_ms={} total_ms={}",
                id, quick ? "quick" : "deep", response.getStatus(), firstTokenMs, elapsedMs());
        }

        private long elapsedMs() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        }

        private void send(StreamEvent event) {
            try {
                emitter.send(SseEmitter.event().data(event));
            } catch (IOException | IllegalStateException error) {
                end("interrupted");
                throw new CancellationException();
            }
        }
    }
}
