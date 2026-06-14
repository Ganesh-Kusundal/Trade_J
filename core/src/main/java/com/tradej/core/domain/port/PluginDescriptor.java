package com.tradej.core.domain.port;

import java.util.List;

/**
 * Base descriptor for all plugin types. Carries version and compatibility
 * metadata used by the runtime to validate plugin compatibility.
 *
 * @param name              unique plugin identifier
 * @param displayName       human-readable name
 * @param version           semantic version (e.g. "1.2.0")
 * @param minPlatformVersion minimum platform version this plugin requires
 * @param maxPlatformVersion maximum platform version this plugin supports (-1 = unlimited)
 * @param dependencies      list of plugin names this plugin depends on
 */
public record PluginDescriptor(
        String name,
        String displayName,
        String version,
        int minPlatformVersion,
        int maxPlatformVersion,
        List<String> dependencies
) {
    public static final int CURRENT_PLATFORM_VERSION = 1;

    public PluginDescriptor {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (displayName == null || displayName.isBlank()) displayName = name;
        if (version == null || version.isBlank()) version = "1.0.0";
        if (dependencies == null) dependencies = List.of();
    }

    public PluginDescriptor(String name, String displayName, String version) {
        this(name, displayName, version, 1, -1, List.of());
    }

    public boolean isCompatibleWith(int platformVersion) {
        return platformVersion >= minPlatformVersion
                && (maxPlatformVersion < 0 || platformVersion <= maxPlatformVersion);
    }

    public boolean isCompatible() {
        return isCompatibleWith(CURRENT_PLATFORM_VERSION);
    }
}
