package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.ScanCriterion;

import java.util.List;

/**
 * Service Provider Interface for scanner profile plugins.
 *
 * <p>Implementations bundle a named set of {@link ScanCriterion} instances
 * that form a scanner profile (e.g., "momentum", "mean-reversion").
 *
 * <p>To register a new scanner profile:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Create {@code META-INF/services/com.tradej.scanner.spi.ScannerProvider}
 *       containing the fully qualified class name</li>
 * </ol>
 */
public interface ScannerProvider {

    String name();

    default String displayName() {
        return name();
    }

    List<ScanCriterion> criteria();

    default boolean isEnabled() {
        return true;
    }

    default String version() {
        return "1.0.0";
    }
}
