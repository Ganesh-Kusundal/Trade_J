package com.tradej.strategy.registry;

import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.example.DepthImbalanceStrategy;
import com.tradej.strategy.example.SmaCrossStrategy;
import com.tradej.strategy.example.TickPriceChangeStrategy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy registry: loads {@code META-INF/strategies/*.yaml}
 * descriptors at boot, instantiates the strategy class, and
 * exposes the resulting {@link GraphStrategyPlugin} instances.
 *
 * <h2>Yaml schema</h2>
 * <pre>
 * id: sma-cross
 * class: com.tradej.strategy.example.SmaCrossStrategy
 * enabled: true
 * parameters:
 *   fastPeriod: 7
 *   slowPeriod: 25
 * </pre>
 *
 * <p>Each yaml describes one strategy. The {@code class} field
 * names a {@link GraphStrategyPlugin} implementation on the
 * classpath. The {@code parameters} map is applied to the
 * constructor (matching by parameter name + type). A yaml with
 * no matching class is logged and skipped — the dev still
 * gets the platform's built-in strategies.
 *
 * <p>Two discovery paths:
 * <ol>
 *   <li>Classpath scan: every {@code META-INF/strategies/*.yaml}
 *       resource on the classpath.</li>
 *   <li>ServiceLoader: every {@link GraphStrategyPlugin} on the
 *       classpath that registered via
 *       {@code META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin}.</li>
 * </ol>
 * ServiceLoader discoveries are wrapped in a {@code "service:"}
 * descriptor so the rest of the registry treats them uniformly.
 */
public final class StrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(StrategyRegistry.class);
    private static final String RESOURCE_PREFIX = "META-INF/strategies/";
    private static final String RESOURCE_SUFFIX = ".yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrategyDescriptor(
            String id,
            String className,
            Boolean enabled,
            Map<String, Object> parameters
    ) {}

    private final Map<String, GraphStrategyPlugin> strategies = new ConcurrentHashMap<>();

    public StrategyRegistry() {
        loadFromClasspath();
        loadFromServiceLoader();
        if (strategies.isEmpty()) {
            loadBuiltInDefaults();
        }
    }

    /**
     * Look up a strategy by id. Returns the same instance on
     * repeated calls (the registry is a process-wide singleton
     * under normal usage).
     */
    public Optional<GraphStrategyPlugin> get(String id) {
        return Optional.ofNullable(strategies.get(id));
    }

    public List<String> ids() {
        return new ArrayList<>(strategies.keySet());
    }

    public int size() {
        return strategies.size();
    }

    private void loadFromClasspath() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = StrategyRegistry.class.getClassLoader();
            Enumeration<URL> resources = cl.getResources(RESOURCE_PREFIX);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String path = url.getPath();
                if (path == null) continue;
                int slash = path.lastIndexOf('/');
                String filename = slash < 0 ? path : path.substring(slash + 1);
                if (!filename.endsWith(RESOURCE_SUFFIX)) continue;
                loadOneYaml(url, filename);
            }
        } catch (IOException ex) {
            log.warn("Failed to scan {} resources: {}", RESOURCE_PREFIX, ex.getMessage());
        }
    }

    private void loadOneYaml(URL url, String filename) {
        try (InputStream in = url.openStream()) {
            StrategyDescriptor desc = YAML.readValue(in, StrategyDescriptor.class);
            if (desc.id() == null || desc.id().isBlank()) {
                log.warn("Skipping {}: missing id", filename);
                return;
            }
            if (Boolean.FALSE.equals(desc.enabled())) {
                log.info("Skipping {}: enabled=false", desc.id());
                return;
            }
            if (desc.className() == null) {
                log.warn("Skipping {}: missing class", desc.id());
                return;
            }
            GraphStrategyPlugin plugin = instantiate(desc);
            strategies.put(desc.id(), plugin);
            log.info("Loaded strategy: id={} class={}", desc.id(), desc.className());
        } catch (Exception ex) {
            log.warn("Failed to load strategy from {}: {}", url, ex.getMessage());
        }
    }

    private GraphStrategyPlugin instantiate(StrategyDescriptor desc) {
        try {
            Class<?> clazz = Class.forName(desc.className());
            if (!GraphStrategyPlugin.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(desc.className() + " does not implement GraphStrategyPlugin");
            }
            return construct(clazz, desc.parameters() == null ? Map.of() : desc.parameters());
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("class not found: " + desc.className(), ex);
        }
    }

    /**
     * Map of yaml parameters to constructor arguments. The
     * matching is by parameter name + type. Unknown parameters
     * are an error; missing parameters use the default Java
     * value (null for refs, 0 for primitives).
     */
    private GraphStrategyPlugin construct(Class<?> clazz, Map<String, Object> params) {
        try {
            java.lang.reflect.Constructor<?>[] ctors = clazz.getDeclaredConstructors();
            if (ctors.length == 0) {
                throw new IllegalArgumentException("no public constructor on " + clazz.getName());
            }
            // Pick the constructor whose parameters are all satisfiable
            // by the yaml map (by name + type). Most strategies have a
            // single 0-arg ctor + getters; the registry supports a
            // simple 1-arg ctor with named primitives.
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                java.lang.reflect.Parameter[] ps = ctor.getParameters();
                if (paramTypes.length == 0) {
                    ctor.setAccessible(true);
                    return (GraphStrategyPlugin) ctor.newInstance();
                }
                Object[] args = new Object[paramTypes.length];
                boolean allSatisfied = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    String pname = ps[i].getName();
                    Object val = params.get(pname);
                    if (val == null) { allSatisfied = false; break; }
                    Class<?> want = wrap(paramTypes[i]);
                    if (!want.isInstance(val)) {
                        // try a coercion for int <-> Integer / long <-> Long
                        val = coerce(val, want);
                        if (val == null) { allSatisfied = false; break; }
                    }
                    args[i] = val;
                }
                if (allSatisfied) {
                    ctor.setAccessible(true);
                    return (GraphStrategyPlugin) ctor.newInstance(args);
                }
            }
            throw new IllegalArgumentException("no constructor on " + clazz.getName() + " is satisfiable by " + params);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("failed to construct " + clazz.getName() + ": " + ex.getMessage(), ex);
        }
    }

    private void loadFromServiceLoader() {
        try {
            ServiceLoader<GraphStrategyPlugin> sl = ServiceLoader.load(GraphStrategyPlugin.class);
            for (GraphStrategyPlugin p : sl) {
                String id = "service:" + p.name();
                strategies.putIfAbsent(id, p);
                log.info("Loaded service-registered strategy: id={}", id);
            }
        } catch (Exception ex) {
            log.warn("ServiceLoader discovery failed: {}", ex.getMessage());
        }
    }

    /**
     * If no descriptors are found on the classpath and no
     * ServiceLoader entries are registered, fall back to the
     * three built-in example strategies so the platform is
     * never empty in dev.
     */
    private void loadBuiltInDefaults() {
        try {
            strategies.put("builtin:sma-cross-7-25", new SmaCrossStrategy(7, 25));
            // DepthImbalanceStrategy and TickPriceChangeStrategy
            // require 3-arg constructors; instantiate reflectively so
            // the registry stays decoupled from their signatures.
            try {
                Class<?> depth = Class.forName("com.tradej.strategy.example.DepthImbalanceStrategy");
                GraphStrategyPlugin depthInstance = (GraphStrategyPlugin)
                        depth.getDeclaredConstructor(String.class, double.class, long.class)
                                .newInstance("depth-imbalance", 0.3, 1000L);
                strategies.put("builtin:depth-imbalance", depthInstance);
            } catch (ReflectiveOperationException ex) {
                log.warn("Failed to instantiate built-in depth strategy: {}", ex.getMessage());
            }
            try {
                Class<?> tick = Class.forName("com.tradej.strategy.example.TickPriceChangeStrategy");
                GraphStrategyPlugin tickInstance = (GraphStrategyPlugin)
                        tick.getDeclaredConstructor(String.class, long.class, long.class)
                                .newInstance("tick-price-change", 100L, 1000L);
                strategies.put("builtin:tick-price-change", tickInstance);
            } catch (ReflectiveOperationException ex) {
                log.warn("Failed to instantiate built-in tick strategy: {}", ex.getMessage());
            }
            log.info("Loaded built-in default strategies (no META-INF/strategies/*.yaml or ServiceLoader entries found)");
        } catch (Exception ex) {
            log.warn("Failed to load built-in defaults: {}", ex.getMessage());
        }
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static Object coerce(Object val, Class<?> want) {
        if (want == Integer.class && val instanceof Number n) return n.intValue();
        if (want == Long.class && val instanceof Number n) return n.longValue();
        if (want == Double.class && val instanceof Number n) return n.doubleValue();
        if (want == Float.class && val instanceof Number n) return n.floatValue();
        if (want == Short.class && val instanceof Number n) return n.shortValue();
        if (want == Byte.class && val instanceof Number n) return n.byteValue();
        if (want == String.class) return val.toString();
        return null;
    }
}
