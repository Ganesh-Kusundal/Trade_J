package com.tradej.optimizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Declares the search space for a single optimization parameter. */
public record ParameterSpace(
        String name,
        ParameterType type,
        Optional<BigDecimal> minValue,
        Optional<BigDecimal> maxValue,
        Optional<BigDecimal> step,
        List<Object> discreteValues
) {}
