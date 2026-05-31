package com.tradej.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class OutputFormatter {
    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final boolean json;

    public OutputFormatter(boolean json) {
        this.json = json;
    }

    public void print(Object value) {
        if (json) {
            System.out.println(PRETTY.valueToTree(value).toPrettyString());
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
