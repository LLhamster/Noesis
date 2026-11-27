package com.example.httpreading.api;

import com.example.httpreading.common.security.JwtAuthFilter;
import com.example.httpreading.common.web.ErrorCode;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.service.AiChatRateLimiter;
import com.example.httpreading.service.ai.ReadingChatStreamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai/chat/streams")
public class AiChatStreamingController {
    private final ReadingChatStreamService streams;
    private final AiChatRateLimiter rateLimiter;

    public AiChatStreamingController(ReadingChatStreamService streams, AiChatRateLimiter rateLimiter) {
        this.streams = streams;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(path = "/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter start(@PathVariable UUID id, @Valid @RequestBody AiChatRequest body,
                            HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (!rateLimiter.tryAcquire(userId.toString())) throw ErrorCode.TOO_MANY_REQUESTS.toException();
        return streams.start(userId, id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable UUID id, HttpServletRequest request) {
        streams.stop(currentUserId(request), id);
    }

    private static Long currentUserId(HttpServletRequest request) {
        if (request.getAttribute(JwtAuthFilter.ATTR_USER_ID) instanceof Long id) return id;
        throw ErrorCode.UNAUTHORIZED.toException();
    }
}
