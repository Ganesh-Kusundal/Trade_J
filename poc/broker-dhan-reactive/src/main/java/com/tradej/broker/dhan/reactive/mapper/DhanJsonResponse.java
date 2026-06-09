package com.tradej.broker.dhan.reactive.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrapper for JSON responses from Dhan API.
 */
public class DhanJsonResponse {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final JsonNode jsonNode;
    
    public DhanJsonResponse(String json) {
        try {
            this.jsonNode = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON response", e);
        }
    }
    
    public DhanJsonResponse(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }
    
    public boolean has(String field) {
        return jsonNode.has(field);
    }
    
    public JsonNode get(String field) {
        return jsonNode.get(field);
    }
    
    public String asString() {
        return jsonNode.asText();
    }
    
    public long asLong() {
        return jsonNode.asLong();
    }
    
    public JsonNode raw() {
        return jsonNode;
    }
    
    /**
     * Get the raw JsonNode (alias for raw()).
     */
    public JsonNode json() {
        return jsonNode;
    }
}
