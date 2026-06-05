package com.tradej.core.domain.model;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

import java.util.List;

/**
 * Preview of an order before placement, showing estimated costs, margin requirements,
 * and validation results.
 */
public record OrderPreview(
        String symbol,
        ExchangeSegment exchangeSegment,
        Side side,
        long quantity,
        long pricePaisa,
        long triggerPricePaisa,
        ProductType productType,
        long estimatedNotionalPaisa,
        long estimatedMarginPaisa,
        boolean valid,
        List<ValidationIssue> issues
) {
    /**
     * Validation issue found during preview.
     */
    public record ValidationIssue(
            Severity severity,
            String code,
            String message,
            String field,
            Object rejectedValue,
            String suggestion
    ) {
        public enum Severity {
            ERROR,  // Will reject the order
            WARN    // Will allow but warns the user
        }
    }

    public static OrderPreview valid(
            String symbol,
            ExchangeSegment exchangeSegment,
            Side side,
            long quantity,
            long pricePaisa,
            long triggerPricePaisa,
            ProductType productType,
            long estimatedNotionalPaisa,
            long estimatedMarginPaisa
    ) {
        return new OrderPreview(symbol, exchangeSegment, side, quantity, pricePaisa, triggerPricePaisa,
                productType, estimatedNotionalPaisa, estimatedMarginPaisa, true, List.of());
    }

    public static OrderPreview valid(
            String symbol,
            ExchangeSegment exchangeSegment,
            Side side,
            long quantity,
            long pricePaisa,
            long triggerPricePaisa,
            ProductType productType,
            long estimatedNotionalPaisa,
            long estimatedMarginPaisa,
            List<ValidationIssue> issues
    ) {
        return new OrderPreview(symbol, exchangeSegment, side, quantity, pricePaisa, triggerPricePaisa,
                productType, estimatedNotionalPaisa, estimatedMarginPaisa, true, issues);
    }

    public static OrderPreview invalid(
            String symbol,
            ExchangeSegment exchangeSegment,
            Side side,
            long quantity,
            long pricePaisa,
            long triggerPricePaisa,
            ProductType productType,
            long estimatedNotionalPaisa,
            long estimatedMarginPaisa,
            List<ValidationIssue> issues
    ) {
        return new OrderPreview(symbol, exchangeSegment, side, quantity, pricePaisa, triggerPricePaisa,
                productType, estimatedNotionalPaisa, estimatedMarginPaisa, false, issues);
    }

    public static OrderPreview invalid(
            String symbol,
            ExchangeSegment exchangeSegment,
            Side side,
            long quantity,
            long pricePaisa,
            long triggerPricePaisa,
            ProductType productType,
            long estimatedNotionalPaisa,
            long estimatedMarginPaisa
    ) {
        return new OrderPreview(symbol, exchangeSegment, side, quantity, pricePaisa, triggerPricePaisa,
                productType, estimatedNotionalPaisa, estimatedMarginPaisa, false, List.of());
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
    }

    public boolean hasWarnings() {
        return issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.WARN);
    }

    public List<ValidationIssue> errors() {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.WARN).toList();
    }
}