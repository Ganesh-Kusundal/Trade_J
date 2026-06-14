package com.tradej.mcp;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP health check endpoint. The actual MCP protocol (SSE transport, tool registration,
 * JSON-RPC handling) is managed by Spring AI's MCP Server WebMVC auto-configuration.
 *
 * <p>Tool classes annotated with {@code @McpTool} are auto-discovered and registered:
 * <ul>
 *   <li>{@link com.tradej.mcp.tools.AnalyticsTools} — all analytics (market, equity, options, sync)</li>
 * </ul>
 *
 * <p>MCP clients connect to {@code /sse} for the SSE transport endpoint.
 */
@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private final DuckDbAnalyticsEngine analyticsEngine;

    public McpServerController(DuckDbAnalyticsEngine analyticsEngine) {
        this.analyticsEngine = analyticsEngine;
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        body.put("transport", "SSE");
        body.put("endpoint", "/sse");
        body.put("optionsAttached", analyticsEngine.optionsAttached());
        body.put("runtimeAttached", analyticsEngine.runtimeAttached());
        try {
            var catalog = analyticsEngine.catalogSnapshot();
            body.put("equitySymbols", catalog.equitySymbolCount());
            body.put("equityDateRange", catalog.equityMinMonth() + " to " + catalog.equityMaxMonth());
            body.put("optionsBarCount", catalog.optionsBarCount());
        } catch (Exception ex) {
            body.put("catalogError", ex.getMessage());
        }
        return ResponseEntity.ok(body);
    }
}
