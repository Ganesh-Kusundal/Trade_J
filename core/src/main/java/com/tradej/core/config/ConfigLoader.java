package com.tradej.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Framework-agnostic configuration loader.
 * Reads properties from files and environment variables without Spring dependency.
 */
public final class ConfigLoader {

    private final Properties properties;

    private ConfigLoader(Properties properties) {
        this.properties = properties;
    }

    public static ConfigLoader load(Path... paths) {
        Properties merged = new Properties();
        for (Path path : paths) {
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    Properties fileProps = new Properties();
                    fileProps.load(in);
                    merged.putAll(fileProps);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read config: " + path, e);
                }
            }
        }
        return new ConfigLoader(merged);
    }

    public static ConfigLoader fromProperties(Properties properties) {
        Properties copy = new Properties();
        copy.putAll(properties);
        return new ConfigLoader(copy);
    }

    public String get(String key) {
        return resolve(key, null);
    }

    public String get(String key, String defaultValue) {
        return resolve(key, defaultValue);
    }

    public String require(String key) {
        String value = resolve(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required configuration missing: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = resolve(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    public long getLong(String key, long defaultValue) {
        String value = resolve(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value.trim());
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = resolve(key, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    private String resolve(String key, String defaultValue) {
        String envKey = key.toUpperCase().replace('.', '_').replace('-', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String propValue = System.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        String fileValue = properties.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }
        return defaultValue;
    }
}
