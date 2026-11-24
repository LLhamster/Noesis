package com.example.httpreading.modules.profile.service;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.ArrayList;
import java.util.List;

import com.example.httpreading.modules.profile.dto.ProfileDtos.KnowledgeStatePatch;
import com.example.httpreading.modules.profile.dto.ProfileDtos.NewEvidencePatch;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ProfileUpdatePatch;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ReadingProfilePatch;
import com.example.httpreading.modules.profile.dto.ProfileDtos.ReadingUnderstandingProfileDto;
import com.example.httpreading.modules.profile.dto.ProfileDtos.StyleProfileDto;
import com.example.httpreading.modules.profile.dto.ProfileDtos.UserKnowledgeStateDto;
import com.example.httpreading.modules.memory.model.MemoryItem;
import com.example.httpreading.service.ModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProfilePatchExtractor {
    private static final Logger log = LoggerFactory.getLogger(ProfilePatchExtractor.class);

    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final ProfileJson profileJson;
    private final BookCategoryService bookCategoryService;
    private final UserKnowledgeStateService knowledgeStateService;

    public ProfilePatchExtractor(ModelClient modelClient,
                                 ObjectMapper objectMapper,
                                 ProfileJson profileJson,
                                 BookCategoryService bookCategoryService,
                                 UserKnowledgeStateService knowledgeStateService) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.profileJson = profileJson;
        this.bookCategoryService = bookCategoryService;
        this.knowledgeStateService = knowledgeStateService;
    }

    public ExtractedProfilePatch extract(List<MemoryItem> memories,
                                         StyleProfileDto oldStyle,
                                         List<ReadingUnderstandingProfileDto> oldReadings,
                                         List<UserKnowledgeStateDto> oldKnowledgeStates,
                                         String fallbackCategory,
                                         String question) {
        String prompt = buildPatchPrompt(
            memories,
            profileJson.writeObject(oldStyle),
            profileJson.writeObject(oldReadings == null ? List.of() : oldReadings),
            profileJson.writeObject(oldKnowledgeStates == null ? List.of() : oldKnowledgeStates),
            fallbackCategory,
            question);
        String rawPatch = modelClient.chat(prompt);
        return parsePatchWithRetry(rawPatch, prompt);
    }

    private ExtractedProfilePatch parsePatchWithRetry(String rawPatch, String originalPrompt) {
        try {
            return parseAndValidatePatch(rawPatch);
        } catch (Exception exception) {
            String firstError = exception.getMessage();
            log.warn("画像 patch 首次解析失败，准备重试修复: {}", firstError);
            String retryRaw = modelClient.chat(buildRetryPrompt(originalPrompt, rawPatch, firstError));
            try {
                return parseAndValidatePatch(retryRaw);
            } catch (Exception retryException) {
                log.warn("画像 patch 重试解析仍失败: {}", retryException.getMessage());
                return null;
            }
        }
    }

    private ExtractedProfilePatch parseAndValidatePatch(String rawPatch) throws Exception {
        String normalized = extractJson(rawPatch);
        JsonNode root = objectMapper.readTree(normalized);
        validateRoot(root);
        ProfileUpdatePatch patch = objectMapper.treeToValue(root, ProfileUpdatePatch.class);

        List<ReadingProfilePatch> readings = new ArrayList<>();
        for (ReadingProfilePatch reading : patch.readingPatches() == null ? List.<ReadingProfilePatch>of() : patch.readingPatches()) {
            if (!bookCategoryService.isAllowed(reading.bookCategory())) {
                throw new IllegalArgumentException("readingPatches.bookCategory 必须属于固定枚举: " + reading.bookCategory());
            }
            if (isStylePreferenceReadingPatch(reading)) {
                continue;
            }
            readings.add(new ReadingProfilePatch(
                bookCategoryService.normalize(reading.bookCategory()),
                reading.understandingLevel(),
                reading.learningStage(),
                reading.strengths(),
                reading.weaknesses(),
                reading.preferredExplanation(),
                reading.backgroundNeeds(),
                reading.typicalQuestions(),
                reading.summary(),
                reading.confidenceDelta()));
        }
        List<NewEvidencePatch> evidences = new ArrayList<>();
        for (NewEvidencePatch evidence : patch.newEvidence() == null ? List.<NewEvidencePatch>of() : patch.newEvidence()) {
            String domain = "style".equals(evidence.evidenceDomain()) ? "style" : "reading_understanding";
            String category = "reading_understanding".equals(domain)
                ? bookCategoryService.normalize(evidence.bookCategory())
                : null;
            if ("reading_understanding".equals(domain) && isStylePreferenceText(evidence.content())) {
                domain = "style";
                category = null;
            }
            evidences.add(new NewEvidencePatch(domain, evidence.evidenceType(), category,
                evidence.content(), evidence.importance(), evidence.relatedBookId(),
                blankToNull(evidence.relatedBookTitle()), evidence.relatedChapterIndex()));
        }
        List<KnowledgeStatePatch> knowledgePatches = new ArrayList<>();
        for (KnowledgeStatePatch knowledge : patch.knowledgePatches() == null ? List.<KnowledgeStatePatch>of() : patch.knowledgePatches()) {
            knowledgePatches.add(new KnowledgeStatePatch(
                bookCategoryService.normalize(knowledge.domain()),
                knowledge.topic().trim(),
                knowledge.knowledgeType(),
                knowledge.level(),
                knowledge.confidenceDelta(),
                knowledge.masteredEvidence(),
                knowledge.weaknessEvidence(),
                knowledge.summary(),
                knowledge.relatedBookId(),
                blankToNull(knowledge.relatedBookTitle()),
                knowledge.relatedChapterIndex()));
        }
        return new ExtractedProfilePatch(
            new ProfileUpdatePatch(patch.stylePatch(), readings, knowledgePatches, evidences, patch.summary()),
            normalized);
    }

    private String buildPatchPrompt(List<MemoryItem> memories,
                                    String oldStyleSnapshot,
                                    String oldReadingSnapshot,
                                    String oldKnowledgeSnapshot,
                                    String fallbackCategory,
                                    String question) {
        String resolvedCategory = bookCategoryService.normalize(fallbackCategory);
        return PromptTemplates.render("profile/patch",
            param("category", resolvedCategory),
            param("oldStyleSnapshot", oldStyleSnapshot),
            param("oldReadingSnapshot", oldReadingSnapshot),
            param("oldKnowledgeSnapshot", oldKnowledgeSnapshot),
            param("question", question == null ? "" : question),
            param("memoryEvidence", (memories == null ? List.<MemoryItem>of() : memories).stream()
                .map(this::memoryPromptLine)
                .toList()));
    }

    private String buildRetryPrompt(String originalPrompt, String invalidOutput, String parseError) {
        return PromptTemplates.render("profile/patch-retry",
            param("parseError", parseError),
            param("invalidOutput", invalidOutput),
            param("originalPrompt", originalPrompt));
    }

    private String extractJson(String value) {
        if (value == null) {
            return "{}";
        }
        String trimmed = value.trim();
        int fenceStart = trimmed.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = trimmed.indexOf('\n', fenceStart);
            int fenceEnd = trimmed.indexOf("```", contentStart < 0 ? fenceStart + 3 : contentStart + 1);
            if (contentStart >= 0 && fenceEnd > contentStart) {
                return trimmed.substring(contentStart + 1, fenceEnd).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim();
        }
        return trimmed;
    }

    private void validateRoot(JsonNode root) {
        if (!(root instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("画像 patch 顶层必须是 JSON object");
        }
        List<String> allowedTopLevel = List.of("stylePatch", "readingPatches", "knowledgePatches", "newEvidence", "summary");
        objectNode.fieldNames().forEachRemaining(field -> {
            if (!allowedTopLevel.contains(field)) {
                throw new IllegalArgumentException("画像 patch 顶层字段不允许: " + field);
            }
        });
        JsonNode readingPatches = root.get("readingPatches");
        if (readingPatches != null && !readingPatches.isNull()) {
            if (!(readingPatches instanceof ArrayNode arrayNode)) {
                throw new IllegalArgumentException("readingPatches 必须是对象数组");
            }
            for (JsonNode item : arrayNode) {
                if (!(item instanceof ObjectNode readingObject)) {
                    throw new IllegalArgumentException("readingPatches 必须是对象数组");
                }
                rejectForbiddenReadingFields(readingObject);
                JsonNode category = readingObject.get("bookCategory");
                if (category == null || !category.isTextual() || !bookCategoryService.isAllowed(category.asText())) {
                    throw new IllegalArgumentException("readingPatches.bookCategory 必须属于固定枚举");
                }
            }
        }
        JsonNode knowledgePatches = root.get("knowledgePatches");
        if (knowledgePatches != null && !knowledgePatches.isNull()) {
            if (!(knowledgePatches instanceof ArrayNode arrayNode)) {
                throw new IllegalArgumentException("knowledgePatches 必须是对象数组");
            }
            for (JsonNode item : arrayNode) {
                if (!(item instanceof ObjectNode knowledgeObject)) {
                    throw new IllegalArgumentException("knowledgePatches 必须是对象数组");
                }
                validateKnowledgePatch(knowledgeObject);
            }
        }
        JsonNode newEvidence = root.get("newEvidence");
        if (newEvidence != null && !newEvidence.isNull()) {
            if (!(newEvidence instanceof ArrayNode arrayNode)) {
                throw new IllegalArgumentException("newEvidence 必须是对象数组");
            }
            for (JsonNode item : arrayNode) {
                if (!(item instanceof ObjectNode)) {
                    throw new IllegalArgumentException("newEvidence 必须是对象数组，不允许字符串数组");
                }
            }
        }
    }

    private void rejectForbiddenReadingFields(ObjectNode readingPatch) {
        List<String> forbidden = List.of("op", "path", "value", "bookId", "chapterIndex",
            "question", "answer", "readingProgress", "currentChapterIndex");
        for (String field : forbidden) {
            if (readingPatch.has(field)) {
                throw new IllegalArgumentException("readingPatches 包含禁止字段: " + field);
            }
        }
    }

    private void validateKnowledgePatch(ObjectNode knowledgePatch) {
        JsonNode domain = knowledgePatch.get("domain");
        if (domain == null || !domain.isTextual() || !bookCategoryService.isAllowed(domain.asText())) {
            throw new IllegalArgumentException("knowledgePatches.domain 必须属于固定枚举");
        }
        JsonNode topic = knowledgePatch.get("topic");
        if (topic == null || !topic.isTextual() || topic.asText().isBlank()) {
            throw new IllegalArgumentException("knowledgePatches.topic 不能为空");
        }
        JsonNode knowledgeType = knowledgePatch.get("knowledgeType");
        if (knowledgeType == null || !knowledgeType.isTextual()
            || !knowledgeStateService.isAllowedKnowledgeType(knowledgeType.asText())) {
            throw new IllegalArgumentException("knowledgePatches.knowledgeType 不合法");
        }
        JsonNode level = knowledgePatch.get("level");
        if (level == null || !level.isTextual() || !knowledgeStateService.isAllowedLevel(level.asText())) {
            throw new IllegalArgumentException("knowledgePatches.level 不合法");
        }
    }

    private boolean isStylePreferenceReadingPatch(ReadingProfilePatch reading) {
        String text = String.join(" ",
            safeJoin(reading.strengths()),
            safeJoin(reading.weaknesses()),
            safeJoin(reading.preferredExplanation()),
            safeJoin(reading.backgroundNeeds()),
            safeJoin(reading.typicalQuestions()),
            reading.summary() == null ? "" : reading.summary());
        return isStylePreferenceText(text) && !hasUnderstandingSignal(text);
    }

    private boolean isStylePreferenceText(String text) {
        String value = text == null ? "" : text;
        return value.contains("完整案例")
            || value.contains("具体案例")
            || value.contains("背景解释")
            || value.contains("直接进入故事")
            || value.contains("最后回扣")
            || value.contains("回扣原文")
            || value.contains("通俗")
            || value.contains("教科书式")
            || value.contains("模板化")
            || value.contains("空泛");
    }

    private boolean hasUnderstandingSignal(String text) {
        String value = text == null ? "" : text;
        return value.contains("理解程度")
            || value.contains("掌握")
            || value.contains("概念边界")
            || value.contains("概念和案例")
            || value.contains("知识点")
            || value.contains("作者观点")
            || value.contains("批判")
            || value.contains("迁移")
            || value.contains("应用")
            || value.contains("区分")
            || value.contains("总结")
            || value.contains("对应起来");
    }

    private String memoryPromptLine(MemoryItem memory) {
        return "- id=" + memory.getId()
            + ", importance=" + memory.getImportance()
            + ", content=" + memory.getContent()
            + ", metadata=" + profileJson.writeObject(memory.getMetadata());
    }

    private String safeJoin(List<String> values) {
        return values == null ? "" : String.join(" ", values);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ExtractedProfilePatch(ProfileUpdatePatch patch, String rawJson) {
    }
}
