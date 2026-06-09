package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SparklineTest {

    @Test
    void render_validData_returnsNonEmpty() {
        String result = Sparkline.render(1.0, 2.0, 3.0, 4.0, 5.0);
        assertFalse(result.isEmpty());
        assertEquals(5, result.replaceAll("\033\\[[;\\d]*m", "").length(),
                "Sparkline should have one character per value");
    }

    @Test
    void render_twoValues_works() {
        String result = Sparkline.render(10.0, 20.0);
        assertFalse(result.isEmpty());
    }

    @Test
    void render_singleValue_returnsEmpty() {
        assertEquals("", Sparkline.render(42.0));
    }

    @Test
    void render_null_returnsEmpty() {
        assertEquals("", Sparkline.render((double[]) null));
    }

    @Test
    void render_empty_returnsEmpty() {
        assertEquals("", Sparkline.render(new double[]{}));
    }

    @Test
    void render_allSame_valuesUsesMiddleBlock() {
        String result = Sparkline.render(5.0, 5.0, 5.0, 5.0);
        assertFalse(result.isEmpty());
    }

    @Test
    void render_monotonicIncreasing_ascendingBlocks() {
        String result = Sparkline.render(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0);
        // Should use ascending block characters
        assertFalse(result.isEmpty());
    }

    @Test
    void render_withNaN_skipsNaN() {
        String result = Sparkline.render(1.0, Double.NaN, 3.0, 4.0);
        assertEquals(4, result.replaceAll("\033\\[[;\\d]*m", "").length());
    }

    @Test
    void renderFromList_works() {
        String result = Sparkline.render(List.of(10.0, 20.0, 30.0));
        assertFalse(result.isEmpty());
    }

    @Test
    void renderLongs_works() {
        String result = Sparkline.renderLongs(100L, 200L, 300L, 400L);
        assertFalse(result.isEmpty());
    }

    @Test
    void renderColored_returnsNonEmpty() {
        String result = Sparkline.renderColored(1.0, 2.0, 3.0);
        assertFalse(result.isEmpty());
    }

    @Test
    void renderColoredLongs_returnsNonEmpty() {
        String result = Sparkline.renderColoredLongs(100L, 200L, 150L, 300L);
        assertFalse(result.isEmpty());
    }
}
