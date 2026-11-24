package com.example.httpreading.service.ai;

import static com.example.httpreading.prompts.PromptTemplates.param;

import com.example.httpreading.prompts.PromptTemplates;
import java.util.stream.Collectors;

import com.example.httpreading.dto.AiChatRequest;
import com.example.httpreading.modules.mcp.ExternalMcpClientService;
import org.springframework.stereotype.Service;

@Service
public class PlannerPromptBuilder {
    private final ExternalMcpClientService externalMcpClientService;

    public PlannerPromptBuilder(ExternalMcpClientService externalMcpClientService) {
        this.externalMcpClientService = externalMcpClientService;
    }

    public String build(AiChatRequest request) {
        return build(request, "");
    }

    public String build(AiChatRequest request, String experimentalPatch) {
        String callableServers = mcpServersText(request.isExternalMcpEnabled());
        String basePrompt = PromptTemplates.render("ai/planner",
            param("userIdJson", json(request.resolvedUserId())),
            param("sessionIdJson", json(request.resolvedSessionId())),
            param("bookId", request.getBookId()),
            param("chapterIndex", request.getChapterIndex()),
            param("chapterTitleJson", json(request.getChapterTitle())),
            param("selectedTextJson", json(request.getSelectedText())),
            param("selectedContextJson", json(request.getSelectedContext())),
            param("memoryEnabled", request.isMemoryEnabled()),
            param("ragEnabled", request.isRagEnabled()),
            param("externalMcpEnabled", request.isExternalMcpEnabled()),
            param("questionJson", json(request.getQuestion())),
            param("callableServers", callableServers));
        return appendExperimentalPatch(basePrompt, experimentalPatch);

    }

    private String appendExperimentalPatch(String basePrompt, String patch) {
        if (patch == null || patch.isBlank()) {
            return basePrompt;
        }
        return basePrompt + PromptTemplates.render("ai/planner-experimental-policy",
            param("experimentalPatch", patch.trim()));
    }

    private String mcpServersText(boolean externalMcpEnabled) {
        String servers = externalMcpClientService.routableServers().stream()
            .filter(server -> externalMcpEnabled || "self-local".equals(String.valueOf(server.get("name"))))
            .map(this::formatMcpServer)
            .collect(Collectors.joining("\n"));
        return servers.isBlank() ? "无。当前没有已启用且带 allowedTools 的 MCP server。" : servers;
    }

    private String formatMcpServer(java.util.Map<String, Object> server) {
        return "- mcp.server:" + server.get("name")
            + "\n  serverName：" + server.get("name")
            + "\n  描述：" + server.get("description")
            + "\n  允许工具名：" + server.get("allowedTools");
    }

    private String json(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
