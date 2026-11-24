package com.example.httpreading.modules.mcp;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.service.ModelClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExternalMcpServerRouterService {
    private static final Logger log = LoggerFactory.getLogger(ExternalMcpServerRouterService.class);
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;

    public ExternalMcpServerRouterService(ModelClient modelClient, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
    }

    public ExternalMcpServerRoute route(AiChatRequest request,
                                        String planningContext,
                                        List<Map<String, Object>> servers) {
        if (servers == null || servers.isEmpty()) {
            return ExternalMcpServerRoute.skip("没有可用的外部 MCP server");
        }
        String raw;
        try {
            raw = modelClient.chat(buildPrompt(request, planningContext, servers));
        } catch (Exception exception) {
            log.warn("MCP server 路由模型调用异常，跳过外部 MCP: {}", exception.getMessage());
            return ExternalMcpServerRoute.skip("MCP server 路由模型调用异常，已跳过外部 MCP");
        }
        if (isModelFailure(raw)) {
            log.warn("MCP server 路由模型调用失败，跳过外部 MCP: {}", raw);
            return ExternalMcpServerRoute.skip("MCP server 路由模型调用失败，已跳过外部 MCP");
        }
        Map<String, Object> decision = parseJson(raw);
        if (decision.isEmpty()) {
            return ExternalMcpServerRoute.skip("MCP server 路由结果无法解析");
        }

        boolean useMcp = readBoolean(decision.get("useMcp"));
        String serverName = normalize(decision.get("serverName"));
        String reason = normalize(decision.get("reason"));
        if (!useMcp) {
            return ExternalMcpServerRoute.skip(reason.isBlank() ? "问题不需要外部 MCP 工具" : reason);
        }

        Set<String> allowedServers = servers.stream()
            .map(server -> normalize(server.get("name")))
            .filter(name -> !name.isBlank())
            .collect(Collectors.toSet());
        if (!allowedServers.contains(serverName)) {
            return ExternalMcpServerRoute.skip("路由选择的 MCP server 不存在或不可用: " + serverName);
        }
        return ExternalMcpServerRoute.use(serverName, reason);
    }

    private String buildPrompt(AiChatRequest request,
                               String planningContext,
                               List<Map<String, Object>> servers) {
        return PromptTemplates.render("mcp/server-router",
            param("question", request == null ? "" : request.getQuestion()),
            param("planningContext", planningContext == null ? "" : planningContext),
            param("serversJson", toJson(servers)));
    }

    private Map<String, Object> parseJson(String raw) {
        String json = extractJson(raw);
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

    private String extractJson(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "";
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(normalize(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return String.valueOf(value);
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isModelFailure(String raw) {
        String value = normalize(raw);
        return value.startsWith("模型接口请求失败")
            || value.startsWith("调用模型接口异常")
            || value.startsWith("模型返回格式不符合预期");
    }
}
