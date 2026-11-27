package com.example.httpreading.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.httpreading.domain.entity.Chapters;
import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.modules.context.builder.ReadingContextCompactionService;
import com.example.httpreading.modules.profile.domain.UserStyleProfile;
import com.example.httpreading.modules.profile.repository.UserStyleProfileRepository;
import com.example.httpreading.repository.ChaptersRepository;
import com.example.httpreading.service.ChaptersService;
import com.example.httpreading.service.ModelClient;
import com.example.httpreading.service.ModelClientException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuickReadingAnswerServiceTest {
    @Mock private ChaptersRepository chapters;
    @Mock private ChaptersService content;
    @Mock private UserStyleProfileRepository profiles;
    @Mock private ModelClient model;
    @Spy private ReadingContextCompactionService compaction;
    @InjectMocks private QuickReadingAnswerService service;

    @Test
    void passesSelectionNearbyTextRecentTurnsAndSavedProfileInOneRequest() {
        AiChatRequest request = request();
        request.setSelectedText("中间段落");
        request.setRecentConversationDialogue("读者：上一题\n助手：上一轮答案");
        Chapters chapter = new Chapters();
        when(chapters.findByBookIdAndChapterIndex(1L, 3)).thenReturn(Optional.of(chapter));
        when(content.resolveContent(chapter)).thenReturn("前面的文字。中间段落。后面的文字。");
        var profile = new UserStyleProfile();
        profile.setExplanationStyle("用日常语言");
        when(profiles.findByUserId("42")).thenReturn(Optional.of(profile));
        when(model.chatOnce(anyString())).thenReturn("一次生成的回答");

        var response = service.answer(request);

        var prompt = ArgumentCaptor.forClass(String.class);
        verify(model).chatOnce(prompt.capture());
        verifyNoMoreInteractions(model);
        assertTrue(prompt.getValue().contains("前面的文字。中间段落。后面的文字。"));
        assertTrue(prompt.getValue().contains("上一轮答案"));
        assertTrue(prompt.getValue().contains("用日常语言"));
        assertEquals(List.of("第 3 章"), response.getSources());
    }

    @Test
    void usesChapterExcerptWithoutSelectionOrProfileAndDoesNotRetryOnFailure() {
        Chapters chapter = new Chapters();
        chapter.setTitle("章节标题");
        when(chapters.findByBookIdAndChapterIndex(1L, 3)).thenReturn(Optional.of(chapter));
        when(content.resolveContent(chapter)).thenReturn("章节正文。");
        when(model.chatOnce(anyString())).thenThrow(new ModelClientException("busy", 503, true));

        var response = service.answer(request());

        var prompt = ArgumentCaptor.forClass(String.class);
        verify(model).chatOnce(prompt.capture());
        verifyNoMoreInteractions(model);
        assertTrue(prompt.getValue().contains("章节正文"));
        assertEquals("model_unavailable", response.getStatus());
    }

    private static AiChatRequest request() {
        var request = new AiChatRequest();
        request.setBookId(1L);
        request.setChapterIndex(3);
        request.setUserId("42");
        request.setQuestion("解释这一段");
        return request;
    }
}
