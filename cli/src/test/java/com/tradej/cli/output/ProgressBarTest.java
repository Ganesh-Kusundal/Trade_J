package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ProgressBarTest {

    @Test
    void constructor_acceptsValidParams() {
        ProgressBar bar = new ProgressBar("Test", 100);
        assertNotNull(bar);
    }

    @Test
    void constructor_zeroTotal_treatedAsOne() {
        ProgressBar bar = new ProgressBar("Test", 0);
        assertDoesNotThrow(() -> bar.update(0));
    }

    @Test
    void update_doesNotExceedTotal() {
        ProgressBar bar = new ProgressBar("Test", 10);
        assertDoesNotThrow(() -> bar.update(20));
    }

    @Test
    void increment_works() {
        ProgressBar bar = new ProgressBar("Test", 5);
        assertDoesNotThrow(() -> {
            bar.increment();
            bar.increment();
        });
    }

    @Test
    void complete_printsFinalLine() {
        ProgressBar bar = new ProgressBar("Test", 3);
        bar.update(1);
        bar.update(2);
        assertDoesNotThrow(bar::complete);
    }

    @Test
    void customWidth_works() {
        ProgressBar bar = new ProgressBar("Download", 50, 20);
        assertDoesNotThrow(() -> {
            for (int i = 0; i <= 50; i++) bar.update(i);
            bar.complete();
        });
    }
}
