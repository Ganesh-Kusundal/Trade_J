package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import java.util.Map;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.MARGIN_REQUIREMENT_PATH;

public final class UpstoxMarginProvider implements MarginProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxMarginProvider(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MarginEstimate estimateMargin(MarginEstimateRequest request) {
        Map<String, Object> payload = Map.of(
                "instrument_key", request.symbol(),
                "quantity", request.quantity(),
                "price", request.pricePaisa() / 100.0,
                "transaction_type", request.side() == com.tradej.core.domain.value.Side.BUY ? "BUY" : "SELL"
        );
        try {
            String json = MAPPER.writeValueAsString(payload);
            JsonNode root = httpClient.postJson(MARGIN_REQUIREMENT_PATH, json);
            JsonNode data = root.get("data");
            long total = data != null && data.has("required_margin")
                    ? (long) (data.get("required_margin").asDouble() * 100) : 0L;
            return new MarginEstimate(total, total, 0L, 0L);
        } catch (Exception e) {
            throw new RuntimeException("Failed to estimate margin", e);
        }
    }
}
