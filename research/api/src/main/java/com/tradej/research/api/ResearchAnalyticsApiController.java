package com.tradej.research.api;

import com.tradej.research.lab.DuckDbResearchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API exposing strategy runs, trade logs, and metrics stored in DuckDB to the UI.
 */
@RestController
@RequestMapping("/api/research/analytics")
public class ResearchAnalyticsApiController {
    private static final Logger log = LoggerFactory.getLogger(ResearchAnalyticsApiController.class);

    private final DuckDbResearchStore researchStore;

    public ResearchAnalyticsApiController(DuckDbResearchStore researchStore) {
        this.researchStore = researchStore;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> getSessions() {
        // Query distinct session IDs from run results
        String sql = "SELECT DISTINCT session_id FROM run_results ORDER BY session_id DESC";
        return executeQuery(sql);
    }

    @GetMapping("/run-results")
    public ResponseEntity<List<Map<String, Object>>> getRunResults(
        @RequestParam(required = false) String sessionId
    ) {
        String sql;
        if (sessionId != null && !sessionId.isBlank()) {
            sql = "SELECT * FROM run_results WHERE session_id = ? ORDER BY start_time_ms DESC";
            return executeQuery(sql, sessionId);
        } else {
            sql = "SELECT * FROM run_results ORDER BY start_time_ms DESC";
            return executeQuery(sql);
        }
    }

    @GetMapping("/trade-log")
    public ResponseEntity<List<Map<String, Object>>> getTradeLog(@RequestParam String runId) {
        String sql = "SELECT * FROM trade_log WHERE run_id = ? ORDER BY entry_time_ms ASC";
        return executeQuery(sql, runId);
    }

    private ResponseEntity<List<Map<String, Object>>> executeQuery(String sql, Object... params) {
        try {
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
                            String name = rs.getMetaData().getColumnName(c);
                            row.put(name, rs.getObject(c));
                        }
                        rows.add(row);
                    }
                    return ResponseEntity.ok(rows);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to execute research query: {}", sql, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
