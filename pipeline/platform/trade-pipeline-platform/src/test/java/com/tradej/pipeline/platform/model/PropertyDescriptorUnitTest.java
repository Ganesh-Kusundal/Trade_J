package com.tradej.pipeline.platform.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class PropertyDescriptorUnitTest {

    @Test
    void ofFactoryCreatesOptionalProperty() {
        PropertyDescriptor pd = PropertyDescriptor.of(
                "symbol", PropertyDescriptor.PropertyType.STRING, "Trading Symbol");
        assertFalse(pd.required());
        assertFalse(pd.hasDefault());
        assertEquals("symbol", pd.name());
    }

    @Test
    void requiredFactorySetsFlag() {
        PropertyDescriptor pd = PropertyDescriptor.required(
                "threshold", PropertyDescriptor.PropertyType.NUMBER, "Threshold");
        assertTrue(pd.required());
    }

    @Test
    void rejectsNullName() {
        assertThrows(NullPointerException.class, () ->
                new PropertyDescriptor(null, PropertyDescriptor.PropertyType.STRING,
                        "d", "", null, false, List.of(), Map.of()));
    }

    @Test
    void defaultValueReturnsTypedValue() {
        PropertyDescriptor pd = new PropertyDescriptor(
                "count", PropertyDescriptor.PropertyType.INTEGER, "Count",
                "", 5, false, List.of(), Map.of());
        assertEquals(Integer.valueOf(5), pd.defaultValue(Integer.class));
    }
}
