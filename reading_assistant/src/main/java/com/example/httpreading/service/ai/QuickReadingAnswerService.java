package com.example.httpreading.service.ai;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.AiChatResponse;
import com.example.httpreading.modules.context.builder.ReadingContextCompactionService;
import com.example.httpreading.modules.profile.repository.UserStyleProfileRepository;
import com.example.httpreading.prompts.PromptTemplates;
import com.example.httpreading.repository.ChaptersRepository;
import com.example.httpreading.service.ChaptersService;
import com.example.httpreading.service.ModelClient;
import com.example.httpreading.service.ModelClientException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuickReadingAnswerService {
    private final ChaptersRepository chapters;
    private final ChaptersService chapterContent;
    private final ReadingContextCompactionService compaction;
    private final UserStyleProfileRepository profiles;
    private final ModelClient model;

    public QuickReadingAnswerService(ChaptersRepository chapters, ChaptersService chapterContent,
                                     ReadingContextCompactionService compaction,
                                     UserStyleProfileRepository profiles, ModelClient model) {
        this.chapters = chapters;
        this.chapterContent = chapterContent;
        this.compaction = compaction;
        this.profiles = profiles;
        this.model = model;
    }

    public AiChatResponse answer(AiChatRequest request) {
        String selection = limit(request.getSelectedText(), 4000);
        String excerpt = chapters.findByBookIdAndChapterIndex(request.getBookId(), request.getChapterIndex())
            .map(chapter -> {
                String text = chapterContent.resolveContent(chapter);
                return selection.isBlank()
                    ? compaction.compactChapter(request.getQuestion(), request.getBookId(),
                        request.getChapterIndex(), chapter.getTitle(), text)
                    : nearby(text, selection);
            })
            .orElse("");
        String profile = profiles.findByUserId(request.resolvedUserId())
            .map(style -> "解释风格：" + limit(style.getExplanationStyle(), 128)
                + "\n偏好深度：" + limit(style.getPreferredDepth(), 64)
                + "\n使用例子：" + Boolean.TRUE.equals(style.getPrefersExamples())
                + "\n分步解释：" + Boolean.TRUE.equals(style.getPrefersStepByStep())
                + "\n摘要：" + limit(style.getSummary(), 1000))
            .orElse("");
        String prompt = PromptTemplates.render("ai/quick-answer",
            param("chapterIndex", request.getChapterIndex()),
            param("question", request.getQuestion()),
            param("selection", selection),
            param("excerpt", excerpt),
            param("dialogue", limit(request.getRecentConversationDialogue(), 12000)),
            param("profile", profile));
        AiChatResponse response;
        try {
            String answer = model.chatOnce(prompt);
            List<String> sources = selection.isBlank() && excerpt.isBlank()
                ? List.of() : List.of("第 " + request.getChapterIndex() + " 章");
            response = new AiChatResponse(answer, sources);
        } catch (ModelClientException error) {
            response = new AiChatResponse("模型暂时无法回答，请稍后重试。", List.of());
            response.setStatus("model_unavailable");
        }
        response.setSessionId(request.resolvedSessionId());
        return response;
    }

    private static String nearby(String text, String selection) {
        if (text == null) return "";
        int from = text.indexOf(selection);
        if (from < 0) return "";
        return text.substring(Math.max(0, from - 600), Math.min(text.length(), from + selection.length() + 600));
    }

    private static String limit(String text, int max) {
        if (text == null) return "";
        return text.substring(0, Math.min(text.length(), max));
    }
}
