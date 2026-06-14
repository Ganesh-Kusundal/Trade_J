package com.tradej.app.plugin;

import com.tradej.core.domain.port.PluginDescriptor;
import com.tradej.core.domain.port.PluginLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * ClassLoader-isolated plugin lifecycle manager.
 * Each plugin JAR gets its own URLClassLoader, ensuring plugin classes
 * don't leak into the platform classloader or conflict with other plugins.
 */
@Component
public class ClassLoaderPluginLifecycleManager implements PluginLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(ClassLoaderPluginLifecycleManager.class);

    private final Map<String, PluginEntry> installed = new ConcurrentHashMap<>();

    record PluginEntry(PluginDescriptor descriptor, URLClassLoader classLoader, Path source) {}

    @Override
    public PluginDescriptor install(Path pluginJar) {
        try {
            log.info("Installing plugin from {}", pluginJar);

            // Create isolated classloader with platform classloader as parent
            URL jarUrl = pluginJar.toUri().toURL();
            URLClassLoader pluginClassLoader = new URLClassLoader(
                    new URL[]{jarUrl},
                    getClass().getClassLoader()
            );

            // Read plugin descriptor from JAR manifest
            PluginDescriptor descriptor = readDescriptor(pluginJar, pluginClassLoader);

            // Validate compatibility
            if (!descriptor.isCompatible()) {
                pluginClassLoader.close();
                throw new PluginLoadException("Plugin '" + descriptor.name()
                        + "' requires platform version " + descriptor.minPlatformVersion()
                        + "-" + descriptor.maxPlatformVersion()
                        + " but current is " + PluginDescriptor.CURRENT_PLATFORM_VERSION);
            }

            // Check for conflicts
            if (installed.containsKey(descriptor.name())) {
                pluginClassLoader.close();
                throw new PluginLoadException("Plugin '" + descriptor.name() + "' is already installed. Use reload() to update.");
            }

            // Register
            installed.put(descriptor.name(), new PluginEntry(descriptor, pluginClassLoader, pluginJar));
            log.info("Plugin '{}' v{} installed successfully (classloader: {})",
                    descriptor.name(), descriptor.version(), pluginClassLoader.hashCode());

            return descriptor;

        } catch (PluginLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new PluginLoadException("Failed to install plugin from " + pluginJar, e);
        }
    }

    @Override
    public void uninstall(String pluginName) {
        PluginEntry entry = installed.remove(pluginName);
        if (entry == null) {
            throw new PluginLoadException("Plugin '" + pluginName + "' is not installed");
        }

        try {
            entry.classLoader().close();
            log.info("Plugin '{}' uninstalled and classloader closed", pluginName);
        } catch (Exception e) {
            log.warn("Error closing classloader for plugin '{}': {}", pluginName, e.getMessage());
        }
    }

    @Override
    public void reload(String pluginName) {
        PluginEntry existing = installed.get(pluginName);
        if (existing == null) {
            throw new PluginLoadException("Plugin '" + pluginName + "' is not installed");
        }

        Path source = existing.source();
        uninstall(pluginName);
        install(source);
        log.info("Plugin '{}' reloaded from {}", pluginName, source);
    }

    @Override
    public List<PluginDescriptor> installed() {
        return installed.values().stream()
                .map(PluginEntry::descriptor)
                .toList();
    }

    @Override
    public boolean isInstalled(String pluginName) {
        return installed.containsKey(pluginName);
    }

    private PluginDescriptor readDescriptor(Path jarPath, URLClassLoader classLoader) throws Exception {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                throw new PluginLoadException("Plugin JAR has no MANIFEST.MF: " + jarPath);
            }

            var attrs = manifest.getMainAttributes();
            String name = attrs.getValue("Plugin-Name");
            String displayName = attrs.getValue("Plugin-Display-Name");
            String version = attrs.getValue("Plugin-Version");
            String minPlatform = attrs.getValue("Plugin-Min-Platform");
            String maxPlatform = attrs.getValue("Plugin-Max-Platform");

            if (name == null || name.isBlank()) {
                throw new PluginLoadException("Plugin-Name not set in MANIFEST.MF: " + jarPath);
            }

            return new PluginDescriptor(
                    name,
                    displayName != null ? displayName : name,
                    version != null ? version : "1.0.0",
                    minPlatform != null ? Integer.parseInt(minPlatform) : 1,
                    maxPlatform != null ? Integer.parseInt(maxPlatform) : -1,
                    List.of()
            );
        }
    }
}
