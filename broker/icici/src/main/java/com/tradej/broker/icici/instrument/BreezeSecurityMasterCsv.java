package com.tradej.broker.icici.instrument;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses ICICI SecurityMaster CSV rows (quoted comma-separated fields).
 */
final class BreezeSecurityMasterCsv {

    private BreezeSecurityMasterCsv() {
    }

    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null || line.isBlank()) {
            return fields;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (ch == ',' && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        fields.add(current.toString().trim());
        return fields;
    }

    static void drain(InputStream inputStream) throws IOException {
        inputStream.transferTo(OutputStream.nullOutputStream());
    }

    static BufferedReader reader(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }

    static String field(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return "";
        }
        return columns.get(index).trim();
    }
}
