package com.tradej.broker.icici.auth;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BreezeApiSessionUrlParser {

    private static final Pattern API_SESSION_IN_TEXT = Pattern.compile(
            "[?&]apisession=([^&\"'\\s<>]+)",
            Pattern.CASE_INSENSITIVE
    );

    private BreezeApiSessionUrlParser() {
    }

    static String parseApiSession(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getQuery();
            if (query == null || query.isBlank()) {
                return parseApiSessionFromText(url);
            }
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                String name = parts[0].toLowerCase(Locale.ROOT);
                if ("apisession".equals(name) || "api_session".equals(name)) {
                    String value = decodeQueryValue(parts[1]).trim();
                    return value.isBlank() ? null : value;
                }
            }
            return parseApiSessionFromText(url);
        } catch (IllegalArgumentException ex) {
            return parseApiSessionFromText(url);
        }
    }

    static String parseApiSessionFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = API_SESSION_IN_TEXT.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = URLDecoder.decode(matcher.group(1).replace("+", "%2B"), StandardCharsets.UTF_8).trim();
        return value.isBlank() ? null : value;
    }

    private static String decodeQueryValue(String encodedValue) {
        return URLDecoder.decode(encodedValue.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}
