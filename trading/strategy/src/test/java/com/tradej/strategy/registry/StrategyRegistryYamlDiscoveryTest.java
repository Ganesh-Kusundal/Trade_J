package com.tradej.strategy.registry;

import com.tradej.strategy.example.SmaCrossStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the shipped yaml descriptor at
 * {@code META-INF/strategies/sma-cross-5-15.yaml} is actually
 * loaded by the {@link StrategyRegistry}. The descriptor is
 * part of the platform's contract: a new quant can drop a
 * class + a yaml into {@code META-INF/strategies/} and the
 * platform picks it up at boot. The other tests verify
 * built-in defaults and the parser; this one verifies the
 * full discovery path.
 *
 * <p>Discovery is "first-source-wins, fallback-empty": the
 * registry loads (in order) classpath yamls, ServiceLoader
 * entries, and only falls back to built-in defaults if BOTH
 * are empty. So when the shipped yaml is on the classpath,
 * the built-in defaults are skipped. This is the intended
 * behavior — the yaml takes precedence.
 */
class StrategyRegistryYamlDiscoveryTest {

    @Test
    void registryDiscoversShippedYamlDescriptor() {
        StrategyRegistry r = new StrategyRegistry();
        // The shipped yaml is at META-INF/strategies/sma-cross-5-15.yaml.
        // It names SmaCrossStrategy with parameters {fastPeriod:5, slowPeriod:15}.
        // The registry should load it as id='sma-cross-5-15'.
        var strategy = r.get("sma-cross-5-15");
        assertTrue(strategy.isPresent(),
                "Shipped META-INF/strategies/sma-cross-5-15.yaml must be loaded at boot");
        assertTrue(strategy.get() instanceof SmaCrossStrategy,
                "Loaded strategy must be the named SmaCrossStrategy class");
    }

    @Test
    void registrySkipsBuiltinsWhenYamlPresent() {
        StrategyRegistry r = new StrategyRegistry();
        // When the shipped yaml is on the classpath, the built-in
        // defaults are skipped (they're a fallback for an empty
        // registry). The yaml provides the strategies.
        boolean hasShipped = r.ids().contains("sma-cross-5-15");
        assertTrue(hasShipped, "Shipped yaml descriptor must be present");
        // The built-ins (id prefix 'builtin:') are not loaded when
        // the yaml is present. The size is the number of yamls
        // + ServiceLoader entries.
        assertTrue(r.ids().stream().noneMatch(id -> id.startsWith("builtin:")),
                "Built-in defaults should be skipped when yaml is on the classpath");
    }

    @Test
    void registryYieldsAtLeastOneStrategy() {
        StrategyRegistry r = new StrategyRegistry();
        assertTrue(r.size() >= 1, "Registry should be non-empty when shipped yaml is on classpath");
    }

    @Test
    void registryYamlStrategyHasCorrectName() {
        // The SmaCrossStrategy's name() method returns the id
        // pattern. The shipped yaml loads it with fast=5, slow=15,
        // so the name should reflect those parameters.
        StrategyRegistry r = new StrategyRegistry();
        var s = r.get("sma-cross-5-15").orElseThrow();
        // SmaCrossStrategy.name() returns "sma-cross-{fast}-{slow}".
        // With fast=5, slow=15 from the yaml, the name is "sma-cross-5-15".
        assertTrue(s.name().contains("5") && s.name().contains("15"),
                "Loaded strategy's name should reflect the yaml parameters (fast=5, slow=15); got: " + s.name());
    }
}
