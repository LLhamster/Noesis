package com.example.httpreading.modules.mcp;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.dto.ExternalMcpCall;
import com.example.httpreading.service.ModelClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ExternalMcpToolPlannerService {
    private static final int MAX_OBSERVATION_CONTENT_CHARS = 3500;
    private static final int MAX_OBSERVATIONS_JSON_CHARS = 9000;
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;

    public ExternalMcpToolPlannerService(ModelClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public ExternalMcpAgentDecision decide(AiChatRequest request,
                                           String planningContext,
                                           List<Map<String, Object>> allowedTools,
                                           List<ExternalMcpAgentObservation> observations,
                                           int round,
                                           int remainingCalls) {
        String rawDecision = modelClient.chat(buildPrompt(
            request, planningContext, allowedTools, observations, round, remainingCalls));
        Map<String, Object> root = parseDecision(rawDecision);
        if (root.isEmpty()) {
            return ExternalMcpAgentDecision.failed("AUTO_PLAN_PARSE_FAILED");
        }

        String status = normalize(root.get("status"));
        String assessment = normalize(root.get("assessment"));
        String reasoningSummary = normalize(root.get("reasoningSummary"));
        String message = normalize(root.get("message"));
        ExternalMcpCall call = parseCall(root.get("call"));
        List<ExternalMcpAgentOption> options = parseOptions(root.get("options"));
        return new ExternalMcpAgentDecision(status, assessment, reasoningSummary, call, message, options);
    }

    private String buildPrompt(AiChatRequest request,
                               String planningContext,
                               List<Map<String, Object>> allowedTools,
                               List<ExternalMcpAgentObservation> observations,
                               int round,
                               int remainingCalls) {
        return PromptTemplates.render("mcp/tool-planner",
            param("question", request == null ? "" : request.getQuestion()),
            param("planningContext", planningContext == null ? "" : planningContext),
            param("round", round),
            param("remainingCalls", remainingCalls),
            param("allowedToolsJson", toJson(allowedTools)),
            param("observationsJson", compactObservations(observations)));
    }

    private String compactObservations(List<ExternalMcpAgentObservation> observations) {
        if (observations == null || observations.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> compact = observations.stream()
            .map(ExternalMcpAgentObservation::toMap)
            .map(this::truncateObservation)
            .toList();
        String json = toJson(compact);
        if (json.length() <= MAX_OBSERVATIONS_JSON_CHARS) {
            return json;
        }
        return json.substring(0, MAX_OBSERVATIONS_JSON_CHARS)
            + "...[observations truncated]";
    }

    private Map<String, Object> truncateObservation(Map<String, Object> observation) {
        Map<String, Object> compact = new LinkedHashMap<>(observation);
        compact.computeIfPresent("content", (key, value) -> truncate(String.valueOf(value)));
        compact.computeIfPresent("error", (key, value) -> truncate(String.valueOf(value)));
        return compact;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_OBSERVATION_CONTENT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_OBSERVATION_CONTENT_CHARS) + "...[tool result truncated]";
    }

    private ExternalMcpCall parseCall(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> map = objectMap(rawMap);
        ExternalMcpCall call = new ExternalMcpCall();
        call.setServerName(normalize(map.get("serverName")));
        call.setToolName(normalize(map.get("toolName")));
        call.setArguments(map.get("arguments") instanceof Map<?, ?> arguments
            ? objectMap(arguments)
            : Map.of());
        return call;
    }

    private List<ExternalMcpAgentOption> parseOptions(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(this::parseOption)
            .filter(option -> !option.getLabel().isBlank())
            .limit(3)
            .toList();
    }

    private ExternalMcpAgentOption parseOption(Map<?, ?> rawMap) {
        Map<String, Object> map = objectMap(rawMap);
        ExternalMcpAgentOption option = new ExternalMcpAgentOption();
        option.setId(normalize(map.get("id")));
        option.setLabel(normalize(map.get("label")));
        option.setDescription(normalize(map.get("description")));
        option.setValue(map.get("value") instanceof Map<?, ?> value
            ? objectMap(value)
            : Map.of());
        return option;
    }

    private Map<String, Object> parseDecision(String rawDecision) {
        String json = extractJson(rawDecision);
        if (json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String extractJson(String rawDecision) {
        if (rawDecision == null) {
            return "";
        }
        String text = rawDecision.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start < 0 || end <= start ? "" : text.substring(start, end + 1);
    }

    private Map<String, Object> objectMap(Map<?, ?> rawMap) {
        Map<String, Object> data = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> data.put(String.valueOf(key), value));
        return data;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }
}
