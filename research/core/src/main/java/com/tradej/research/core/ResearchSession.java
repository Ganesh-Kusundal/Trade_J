package com.tradej.research.core;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tracks a research session including the dates, symbols, and metadata.
 */
public record ResearchSession(
    UUID sessionId,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    List<String> symbols,
    String description,
    long createdAtMs
) {}
