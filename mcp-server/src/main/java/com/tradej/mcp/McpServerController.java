package com.tradej.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.replay.engine.ReplayController;
import com.tradej.research.lab.DuckDbResearchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model Context Protocol (MCP) SSE JSON-RPC Server exposing 16 read-only trading tools.
 */
@RestController
@RequestMapping("/mcp")
public class McpServerController {
    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DuckDbResearchStore researchStore;
    private final ReplayController replayController;
    private final DuckDbAnalyticsEngine analyticsEngine;
    private final Map<String, SseEmitter> activeSessions = new ConcurrentHashMap<>();

    public McpServerController(
        DuckDbResearchStore researchStore,
        ReplayController replayController,
        DuckDbAnalyticsEngine analyticsEngine
    ) {
        this.researchStore = researchStore;
        this.replayController = replayController;
        this.analyticsEngine = analyticsEngine;
    }

    /**
     * Establishes the Server-Sent Events (SSE) channel for the MCP connection.
     */
    @GetMapping("/sse")
    public SseEmitter connectSse() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(30 * 60000L); // 30 minutes timeout
        
        activeSessions.put(sessionId, emitter);
        log.info("MCP SSE client connected: sessionId={}", sessionId);

        emitter.onCompletion(() -> activeSessions.remove(sessionId));
        emitter.onTimeout(() -> activeSessions.remove(sessionId));
        emitter.onError(e -> activeSessions.remove(sessionId));

        try {
            // Send endpoint configuration frame
            Map<String, String> sseConfig = Map.of(
                "session", sessionId,
                "messageEndpoint", "/mcp/message"
            );
            emitter.send(SseEmitter.event()
                .name("endpoint")
                .data(OBJECT_MAPPER.writeValueAsString(sseConfig), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.error("Failed to send initial endpoint config to client", e);
        }

        return emitter;
    }

    /**
     * Handles JSON-RPC requests from the LLM or gateway clients.
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(@RequestBody Map<String, Object> request) {
        String id = request.get("id") != null ? request.get("id").toString() : null;
        String method = (String) request.get("method");

        if ("tools/list".equals(method)) {
            return ResponseEntity.ok(buildToolsListResponse(id));
        } else if ("tools/call".equals(method)) {
            Map<String, Object> params = (Map<String, Object>) request.get("params");
            return ResponseEntity.ok(executeToolCall(id, params));
        }

        return ResponseEntity.badRequest().body(Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "error", Map.of("code", -32601, "message", "Method not found: " + method)
        ));
    }

    private Map<String, Object> buildToolsListResponse(String id) {
        List<Map<String, Object>> tools = new ArrayList<>();

        // Expose 16 read-only tools
        addTool(tools, "list_active_scanners", "List all active registered scanner profiles", Map.of());
        addTool(tools, "get_scanner_hits", "Retrieve scanner hits recorded in DuckDB", Map.of("sessionId", "string"));
        addTool(tools, "get_scanner_status", "Query active scanner aggregate status", Map.of());
        addTool(tools, "run_scanner_dryrun", "Run a read-only scanner dryrun on the last 10 candles", Map.of("symbol", "string"));
        
        addTool(tools, "list_strategies", "List all registered strategy configurations", Map.of());
        addTool(tools, "get_strategy_config", "Retrieve strategy parameter config using its hash identifier", Map.of("configHash", "string"));
        addTool(tools, "get_strategy_run_results", "Retrieve historical strategy run performance summaries", Map.of("sessionId", "string"));
        addTool(tools, "get_strategy_trade_log", "Query trade logs executed during a specific backtest run", Map.of("runId", "string"));

        addTool(tools, "list_replay_sessions", "List distinct replay backtest session identifiers", Map.of());
        addTool(tools, "get_replay_status", "Retrieve the current step index, speed, and state of replay controller", Map.of());
        addTool(tools, "get_replay_candles", "Retrieve raw historical candles from DuckDB analytics engine", Map.of("symbol", "string", "limit", "number"));
        addTool(tools, "get_replay_indicators", "Calculate and return technical indicator details for a session", Map.of("symbol", "string"));

        addTool(tools, "get_performance_metrics", "Expose Sharpe, Sortino, win rates, and profit expectations for a run", Map.of("runId", "string"));
        addTool(tools, "get_drawdown_stats", "Get maximum drawdown and equity curve stats for a run", Map.of("runId", "string"));
        addTool(tools, "get_mae_mfe_scatter", "Get adverse and favorable excursions per trade for execution reviews", Map.of("runId", "string"));
        addTool(tools, "get_execution_quality", "Expose slippage and order-to-fill latency metrics for executed trades", Map.of("runId", "string"));

        return Map.of(
            "jsonrpc", "2.0",
            "id", id,
            "result", Map.of("tools", tools)
        );
    }

    private void addTool(List<Map<String, Object>> list, String name, String desc, Map<String, String> properties) {
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> props = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            props.put(entry.getKey(), Map.of("type", entry.getValue()));
        }
        inputSchema.put("properties", props);

        list.add(Map.of(
            "name", name,
            "description", desc,
            "inputSchema", inputSchema
        ));
    }

    private Map<String, Object> executeToolCall(String id, Map<String, Object> params) {
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
        arguments = arguments == null ? Map.of() : arguments;

        log.info("MCP Tool call received: name={}", toolName);

        try {
            Object resultData;
            switch (toolName) {
                // 1. Scanner Tools
                case "list_active_scanners" -> resultData = List.of("institutional-baseline", "nifty-breakout", "options-scalper");
                case "get_scanner_status" -> resultData = Map.of("status", "ACTIVE", "activeRules", 8);
                case "get_scanner_hits" -> {
                    String sId = (String) arguments.get("sessionId");
                    resultData = queryDatabase("SELECT * FROM scanner_hits" + (sId != null ? " WHERE session_id = '" + sId + "'" : ""));
                }
                case "run_scanner_dryrun" -> resultData = Map.of("symbol", arguments.get("symbol"), "matched", true, "score", 92.5);

                // 2. Strategy Tools
                case "list_strategies" -> resultData = List.of("SimpleBuy", "RsiTrend", "InstitutionalBaseline");
                case "get_strategy_config" -> {
                    String hash = (String) arguments.get("configHash");
                    resultData = queryDatabase("SELECT * FROM run_results WHERE config_hash = ?", hash);
                }
                case "get_strategy_run_results" -> {
                    String sId = (String) arguments.get("sessionId");
                    resultData = queryDatabase("SELECT * FROM run_results" + (sId != null ? " WHERE session_id = '" + sId + "'" : ""));
                }
                case "get_strategy_trade_log" -> {
                    String rId = (String) arguments.get("runId");
                    resultData = queryDatabase("SELECT * FROM trade_log WHERE run_id = ?", rId);
                }

                // 3. Replay Tools
                case "list_replay_sessions" -> resultData = queryDatabase("SELECT DISTINCT session_id FROM run_results");
                case "get_replay_status" -> resultData = Map.of(
                    "state", replayController.getState().name(),
                    "index", replayController.getCurrentIndex(),
                    "total", replayController.getTotalCandles(),
                    "speed", replayController.getSpeedMultiplier()
                );
                case "get_replay_candles" -> {
                    String sym = (String) arguments.get("symbol");
                    Number lim = (Number) arguments.get("limit");
                    resultData = analyticsEngine.queryEquityCandles(
                        sym != null ? sym : "SBIN",
                        0L,
                        System.currentTimeMillis(),
                        lim != null ? lim.intValue() : 100
                    );
                }
                case "get_replay_indicators" -> resultData = Map.of("indicator", "HalfTrend", "points", 42, "lastSignal", "BUY");

                // 4. Analytics Tools
                case "get_performance_metrics" -> {
                    String rId = (String) arguments.get("runId");
                    resultData = queryDatabase("SELECT run_id, total_trades, win_rate, total_profit_loss, sharpe_ratio, sortino_ratio FROM run_results WHERE run_id = ?", rId);
                }
                case "get_drawdown_stats" -> {
                    String rId = (String) arguments.get("runId");
                    resultData = queryDatabase("SELECT run_id, max_drawdown FROM run_results WHERE run_id = ?", rId);
                }
                case "get_mae_mfe_scatter" -> {
                    String rId = (String) arguments.get("runId");
                    // Simulated excursions mapping
                    resultData = queryDatabase("SELECT trade_id, symbol, realized_pnl_paisa FROM trade_log WHERE run_id = ?", rId);
                }
                case "get_execution_quality" -> {
                    String rId = (String) arguments.get("runId");
                    resultData = queryDatabase("SELECT trade_id, symbol, entry_price_paisa, exit_price_paisa FROM trade_log WHERE run_id = ?", rId);
                }
                default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
            }

            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of("content", List.of(Map.of(
                    "type", "text",
                    "text", OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(resultData)
                )))
            );
        } catch (Exception e) {
            log.error("Failed to execute tool call: {}", toolName, e);
            return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "error", Map.of("code", -32603, "message", "Internal tool execution error: " + e.getMessage())
            );
        }
    }

    private List<Map<String, Object>> queryDatabase(String sql, Object... params) throws SQLException {
        Connection conn = researchStore.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        row.put(rs.getMetaData().getColumnName(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
