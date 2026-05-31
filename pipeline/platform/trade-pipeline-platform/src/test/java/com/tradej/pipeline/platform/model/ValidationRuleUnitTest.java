package com.tradej.pipeline.platform.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class ValidationRuleUnitTest {

    @Test
    void requiredFactory() {
        ValidationRule rule = ValidationRule.required("mandatory");
        assertEquals(ValidationRule.RuleType.REQUIRED, rule.type());
        assertEquals("mandatory", rule.message());
    }

    @Test
    void minMaxFactories() {
        assertEquals(ValidationRule.RuleType.MIN, ValidationRule.min(0, "x").type());
        assertEquals(0.0, (Double) ValidationRule.min(0, "x").parameter());
        assertEquals(ValidationRule.RuleType.MAX, ValidationRule.max(100, "y").type());
        assertEquals(100.0, (Double) ValidationRule.max(100, "y").parameter());
    }

    @Test
    void oneOfCopiesList() {
        List<Object> values = List.of("a", "b");
        ValidationRule rule = ValidationRule.oneOf(values, "pick one");
        assertEquals(ValidationRule.RuleType.ONE_OF, rule.type());
        assertEquals(List.of("a", "b"), rule.parameter());
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class, () ->
                new ValidationRule(null, "msg", null));
    }
}
