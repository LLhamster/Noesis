package com.example.httpreading.api;

import com.example.httpreading.modules.mcp.ExternalMcpClientService;

import java.util.List;
import java.util.Map;

import com.example.httpreading.common.web.CommonResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mcp/client")
public class ExternalMcpClientController {
    private final ExternalMcpClientService externalMcpClientService;

    public ExternalMcpClientController(ExternalMcpClientService externalMcpClientService) {
        this.externalMcpClientService = externalMcpClientService;
    }

    @GetMapping("/status")
    public CommonResponse<List<Map<String, Object>>> status() {
        return CommonResponse.success(externalMcpClientService.configuredServers());
    }

    @GetMapping("/{serverName}/tools")
    public CommonResponse<List<Map<String, Object>>> tools(@PathVariable String serverName) {
        return CommonResponse.success(externalMcpClientService.listTools(serverName));
    }
}
