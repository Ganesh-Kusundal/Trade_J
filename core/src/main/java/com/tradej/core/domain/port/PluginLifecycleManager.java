package com.tradej.core.domain.port;

import java.nio.file.Path;
import java.util.List;

/**
 * Manages the lifecycle of dynamically loaded plugins.
 * Supports runtime install, uninstall, and reload without JVM restart.
 */
public interface PluginLifecycleManager {

    /**
     * Install a plugin from a JAR file on the filesystem.
     * @param pluginJar path to the plugin JAR
     * @return descriptor of the installed plugin
     * @throws PluginLoadException if the plugin fails to load
     */
    PluginDescriptor install(Path pluginJar);

    /**
     * Uninstall a plugin by name. Removes its classloader and registrations.
     */
    void uninstall(String pluginName);

    /**
     * Reload a plugin — uninstall then reinstall from the same source.
     */
    void reload(String pluginName);

    /**
     * List all currently installed plugins.
     */
    List<PluginDescriptor> installed();

    /**
     * Check if a plugin with the given name is installed.
     */
    boolean isInstalled(String pluginName);

    class PluginLoadException extends RuntimeException {
        public PluginLoadException(String message) { super(message); }
        public PluginLoadException(String message, Throwable cause) { super(message, cause); }
    }
}
