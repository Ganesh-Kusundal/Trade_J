package com.tradej.scanner.criterion;

import com.tradej.scanner.model.ScanContext;

import java.util.ArrayList;
import java.util.List;

public final class CriterionGroup implements ScanCriterion {
    private final List<ScanCriterion> criteria;

    public CriterionGroup(List<ScanCriterion> criteria) {
        this.criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public List<ScanCriterion> children() {
        return criteria;
    }

    public boolean requiresOptionChain() {
        for (ScanCriterion child : criteria) {
            if (child instanceof CriterionGroup group && group.requiresOptionChain()) {
                return true;
            }
            if (child instanceof OptionAwareCriterion aware && aware.requiresOptionChain()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String type() {
        return "group-and";
    }

    @Override
    public boolean matches(ScanContext context) {
        if (criteria.isEmpty()) {
            return context.hasValidQuote();
        }
        return criteria.stream().allMatch(c -> c.matches(context));
    }

    @Override
    public double score(ScanContext context) {
        if (criteria.isEmpty()) {
            return context.hasValidQuote() ? 1.0 : 0.0;
        }
        return criteria.stream().mapToDouble(c -> c.score(context)).sum();
    }

    @Override
    public String reason(ScanContext context) {
        List<String> reasons = new ArrayList<>();
        for (ScanCriterion criterion : criteria) {
            if (criterion.matches(context)) {
                reasons.add(criterion.reason(context));
            }
        }
        return String.join(", ", reasons);
    }
}
