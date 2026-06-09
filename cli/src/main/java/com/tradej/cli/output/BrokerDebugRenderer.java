package com.tradej.cli.output;

import java.util.Map;

/**
 * Renders a broker API debug view showing raw request/response details.
 *
 * <p>Example output:
 * <pre>
 * ── Request ──────────────────────────────────────
 * GET https://api.dhan.co/v2/market/quote
 * Headers:
 *   access-token: eyJhbGci...
 *   client-id: 1234567890
 *   Content-Type: application/json
 *
 * ── Response ─────────────────────────────────────
 * Status: 200 OK  (14ms)
 * Headers:
 *   x-ratelimit-remaining: 95/100
 *   x-request-id: req_abc123
 * Body:
 *   { "data": { "last_price": 2543.50 } }
 *
 * ── Mapping ──────────────────────────────────────
 * Quote{ltpPaisa=254350, openPaisa=253100, ...}
 * </pre>
 */
public final class BrokerDebugRenderer {

    private BrokerDebugRenderer() {}

    /**
     * Render a full debug view for a broker API call.
     */
    public static String render(DebugInfo info) {
        StringBuilder sb = new StringBuilder();

        // Request section
        sb.append(Ansi.dim("── Request ")).append(Ansi.dim("─".repeat(40))).append("\n");
        sb.append(Ansi.bold(info.method())).append(" ").append(info.url()).append("\n");
        if (info.requestHeaders() != null && !info.requestHeaders().isEmpty()) {
            sb.append(Ansi.dim("Headers:")).append("\n");
            for (var entry : info.requestHeaders().entrySet()) {
                sb.append("  ").append(Ansi.cyan(entry.getKey())).append(": ")
                        .append(maskSensitive(entry.getKey(), entry.getValue())).append("\n");
            }
        }
        if (info.requestBody() != null && !info.requestBody().isBlank()) {
            sb.append(Ansi.dim("Body:")).append("\n");
            sb.append("  ").append(truncate(info.requestBody(), 500)).append("\n");
        }
        sb.append("\n");

        // Response section
        sb.append(Ansi.dim("── Response ")).append(Ansi.dim("─".repeat(39))).append("\n");
        sb.append(Ansi.bold("Status: ")).append(statusColor(info.statusCode())).append(" ")
                .append(Ansi.dim("(" + info.latencyMs() + "ms)")).append("\n");
        if (info.responseHeaders() != null && !info.responseHeaders().isEmpty()) {
            sb.append(Ansi.dim("Headers:")).append("\n");
            for (var entry : info.responseHeaders().entrySet()) {
                sb.append("  ").append(Ansi.cyan(entry.getKey())).append(": ")
                        .append(entry.getValue()).append("\n");
            }
        }
        if (info.responseBody() != null && !info.responseBody().isBlank()) {
            sb.append(Ansi.dim("Body:")).append("\n");
            sb.append("  ").append(truncate(prettyJson(info.responseBody()), 1000)).append("\n");
        }
        sb.append("\n");

        // Mapping section
        sb.append(Ansi.dim("── Mapping ")).append(Ansi.dim("─".repeat(40))).append("\n");
        if (info.mappedResult() != null) {
            sb.append(Ansi.dim(info.mappedResult())).append("\n");
        }
        if (info.rateLimitRemaining() != null) {
            sb.append(Ansi.dim("Rate limit: ")).append(info.rateLimitRemaining()).append("\n");
        }
        if (info.errorMessage() != null) {
            sb.append(Ansi.red("Error: ")).append(info.errorMessage()).append("\n");
        }

        return sb.toString();
    }

    private static String statusColor(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return Ansi.green(String.valueOf(statusCode));
        if (statusCode >= 400) return Ansi.red(String.valueOf(statusCode));
        return Ansi.yellow(String.valueOf(statusCode));
    }

    private static String maskSensitive(String key, String value) {
        String lower = key.toLowerCase();
        if (lower.contains("token") || lower.contains("secret") || lower.contains("password")) {
            if (value.length() > 8) {
                return value.substring(0, 6) + "..." + value.substring(value.length() - 2);
            }
            return "***";
        }
        return value;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + Ansi.dim("... (truncated)");
    }

    private static String prettyJson(String json) {
        // Simple JSON pretty-print: add newlines after { and ,
        return json.replaceAll("\\{", "{\n  ")
                .replaceAll(",", ",\n  ")
                .replaceAll("\\}", "\n}");
    }

    /**
     * Debug info record capturing all details of a broker API call.
     */
    public record DebugInfo(
            String method,
            String url,
            Map<String, String> requestHeaders,
            String requestBody,
            int statusCode,
            long latencyMs,
            Map<String, String> responseHeaders,
            String responseBody,
            String mappedResult,
            String rateLimitRemaining,
            String errorMessage
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String method = "GET";
            private String url = "";
            private Map<String, String> requestHeaders = Map.of();
            private String requestBody;
            private int statusCode = 200;
            private long latencyMs;
            private Map<String, String> responseHeaders = Map.of();
            private String responseBody;
            private String mappedResult;
            private String rateLimitRemaining;
            private String errorMessage;

            public Builder method(String method) { this.method = method; return this; }
            public Builder url(String url) { this.url = url; return this; }
            public Builder requestHeaders(Map<String, String> h) { this.requestHeaders = h; return this; }
            public Builder requestBody(String body) { this.requestBody = body; return this; }
            public Builder statusCode(int code) { this.statusCode = code; return this; }
            public Builder latencyMs(long ms) { this.latencyMs = ms; return this; }
            public Builder responseHeaders(Map<String, String> h) { this.responseHeaders = h; return this; }
            public Builder responseBody(String body) { this.responseBody = body; return this; }
            public Builder mappedResult(String result) { this.mappedResult = result; return this; }
            public Builder rateLimitRemaining(String remaining) { this.rateLimitRemaining = remaining; return this; }
            public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
            public DebugInfo build() {
                return new DebugInfo(method, url, requestHeaders, requestBody,
                        statusCode, latencyMs, responseHeaders, responseBody,
                        mappedResult, rateLimitRemaining, errorMessage);
            }
        }
    }
}
