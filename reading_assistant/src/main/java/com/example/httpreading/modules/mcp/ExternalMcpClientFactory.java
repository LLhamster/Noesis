package com.example.httpreading.modules.mcp;

public interface ExternalMcpClientFactory {
    ExternalMcpClientSession create(ExternalMcpClientProperties.Server server);
}
