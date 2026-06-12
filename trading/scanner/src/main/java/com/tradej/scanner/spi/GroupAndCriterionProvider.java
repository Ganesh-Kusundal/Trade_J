package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.CriterionGroup;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.ScanCriterionFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GroupAndCriterionProvider implements ScanCriterionProvider {

    @Override
    public String type() {
        return "group-and";
    }

    @Override
    public String displayName() {
        return "AND Group";
    }

    @Override
    public ScanCriterion create(Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nested = (List<Map<String, Object>>) config.get("criteria");
        List<ScanCriterion> children = new ArrayList<>();
        if (nested != null) {
            for (Map<String, Object> child : nested) {
                children.add(ScanCriterionFactory.fromConfig(child));
            }
        }
        return new CriterionGroup(children);
    }
}
