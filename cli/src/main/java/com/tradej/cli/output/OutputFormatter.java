package com.tradej.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OutputFormatter {
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private final boolean json;

    public OutputFormatter(boolean json) {
        this.json = json;
    }

    public void print(Object value) {
        if (json) {
            // Emit single-line JSON to make it safe for machine parsing + CI log capture.
            if (value instanceof JsonNode node) {
                System.out.println(node.toString());
                return;
            }
            System.out.println(COMPACT.valueToTree(value).toString());
            return;
        }
        if (value instanceof JsonNode node) {
            System.out.println(node.toPrettyString());
            return;
        }
        System.out.println(value);
    }

    public void println(String line) {
        System.out.println(line);
    }

    public void error(String message) {
        System.err.println(message);
    }
}
