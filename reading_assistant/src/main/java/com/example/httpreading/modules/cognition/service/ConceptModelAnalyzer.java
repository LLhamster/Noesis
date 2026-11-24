package com.example.httpreading.modules.cognition.service;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.List;

import com.example.httpreading.modules.cognition.dto.ConceptResolutionRequest;
import com.example.httpreading.service.ModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ConceptModelAnalyzer {
    public static final String PROMPT_VERSION = "concept-resolver-v1";
    public static final String ANALYZER_VERSION = "cognition-phase1-v1";

    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;

    @Value("${model.chatModel:deepseek-chat}")
    private String modelName;

    public ConceptModelAnalyzer(ModelClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public ConceptModelAnalysis analyze(ConceptResolutionRequest request, String candidateName) {
        try {
            String raw = modelClient.chat(prompt(request, candidateName));
            JsonNode root = objectMapper.readTree(extractJson(raw));
            String concept = root.path("concept").asText(candidateName);
            double modelScore = clamp(root.path("confidence").asDouble(0.0d));
            JsonNode context = root.path("contextSupport");
            double contextScore = clamp(context.path("score").asDouble(root.path("contextScore").asDouble(0.0d)));
            List<String> evidence = readEvidence(context.path("evidence"));
            String reason = root.path("reason").asText("模型返回概念判断");
            return new ConceptModelAnalysis(concept, modelScore, contextScore, evidence, reason,
                modelName, PROMPT_VERSION, ANALYZER_VERSION);
        } catch (Exception exception) {
            return new ConceptModelAnalysis(candidateName, 0.0d, 0.0d, List.of(),
                "模型概念分析失败，已降级为规则识别: " + exception.getClass().getSimpleName(),
                modelName, PROMPT_VERSION, ANALYZER_VERSION);
        }
    }

    private String prompt(ConceptResolutionRequest request, String candidateName) {
        return PromptTemplates.render("cognition/concept-analysis",
            param("candidateName", safe(candidateName)),
            param("question", safe(request.question())),
            param("selectedText", safe(request.selectedText())),
            param("selectedContext", safe(request.selectedContext())),
            param("chapterTitle", safe(request.chapterTitle())),
            param("chapterSummary", truncate(request.chapterContent(), 1000)),
            param("dialogueSummary", truncate(request.recentDialogue(), 1000)));
    }

    private List<String> readEvidence(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> evidence = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                evidence.add(value);
            }
        }
        return evidence;
    }

    private String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        String trimmed = raw.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
