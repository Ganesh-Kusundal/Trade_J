package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.ScanCriterion;

/**
 * SPI for pluggable scan criteria. Implementations are auto-discovered via
 * java.util.ServiceLoader using META-INF/services/com.tradej.scanner.spi.ScanCriterionProvider.
 * <p>
 * Each provider returns a stable string {@link #type()} used as the key in
 * scan profile YAML/JSON, plus a factory method {@link #create(java.util.Map)} that
 * constructs a {@link ScanCriterion} instance from a config map.
 * <p>
 * To add a new criterion type, implement this interface and append the
 * fully-qualified class name to the META-INF/services file. No switch edit
 * required.
 */
public interface ScanCriterionProvider {
    /** Stable identifier used in scan profile configs. Must be unique. */
    String type();

    /** Human-readable display name. */
    default String displayName() { return type(); }

    /** Construct a criterion from a config map. */
    ScanCriterion create(java.util.Map<String, Object> config);
}
