package com.tradej.cli.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the scaffold command generates valid, compilable source files
 * with correct package declarations and SPI interface implementations.
 */
@Tag("unit")
class CliScaffoldCommandTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Scaffold strategy generates valid Java source file")
    void scaffoldStrategyGeneratesValidSource() throws IOException {
        var cmd = new CliScaffoldCommand.StrategySubcommand();
        setField(cmd, "name", "TestStrategy");
        setField(cmd, "type", "candle");
        setField(cmd, "pkg", "com.test.strategy");
        setField(cmd, "outputDir", tempDir.toString());

        Integer exitCode = cmd.call();
        assertEquals(0, exitCode);

        Path generated = tempDir.resolve("com/test/strategy/TestStrategy.java");
        assertTrue(Files.exists(generated), "Generated file should exist");

        String content = Files.readString(generated);
        assertTrue(content.contains("package com.test.strategy;"), "Should have correct package");
        assertTrue(content.contains("implements GraphStrategyPlugin"), "Should implement GraphStrategyPlugin");
        assertTrue(content.contains("CandleClosed"), "Candle type strategy should use CandleClosed");
        assertTrue(content.contains("subscribedEventTypes"), "Should have subscribedEventTypes method");
        assertTrue(content.contains("onEvent"), "Should have onEvent method");
    }

    @Test
    @DisplayName("Scaffold tick strategy uses MarketTickEvent")
    void scaffoldTickStrategyUsesMarketTickEvent() throws IOException {
        var cmd = new CliScaffoldCommand.StrategySubcommand();
        setField(cmd, "name", "TickStrategy");
        setField(cmd, "type", "tick");
        setField(cmd, "pkg", "com.test.strategy");
        setField(cmd, "outputDir", tempDir.toString());

        cmd.call();

        Path generated = tempDir.resolve("com/test/strategy/TickStrategy.java");
        String content = Files.readString(generated);
        assertTrue(content.contains("MarketTickEvent"), "Tick type strategy should use MarketTickEvent");
    }

    @Test
    @DisplayName("Scaffold scanner generates valid ScanCriterion")
    void scaffoldScannerGeneratesValidSource() throws IOException {
        var cmd = new CliScaffoldCommand.ScannerSubcommand();
        setField(cmd, "name", "RelativeStrength");
        setField(cmd, "pkg", "com.test.scanner");
        setField(cmd, "outputDir", tempDir.toString());

        Integer exitCode = cmd.call();
        assertEquals(0, exitCode);

        Path generated = tempDir.resolve("com/test/scanner/RelativeStrength.java");
        assertTrue(Files.exists(generated));

        String content = Files.readString(generated);
        assertTrue(content.contains("implements ScanCriterion"), "Should implement ScanCriterion");
        assertTrue(content.contains("matches"), "Should have matches method");
        assertTrue(content.contains("score"), "Should have score method");
    }

    @Test
    @DisplayName("Scaffold indicator generates valid IndicatorProvider")
    void scaffoldIndicatorGeneratesValidSource() throws IOException {
        var cmd = new CliScaffoldCommand.IndicatorSubcommand();
        setField(cmd, "name", "BollingerBands");
        setField(cmd, "pkg", "com.test.indicator");
        setField(cmd, "outputDir", tempDir.toString());

        Integer exitCode = cmd.call();
        assertEquals(0, exitCode);

        Path generated = tempDir.resolve("com/test/indicator/BollingerBands.java");
        assertTrue(Files.exists(generated));

        String content = Files.readString(generated);
        assertTrue(content.contains("implements IndicatorProvider"), "Should implement IndicatorProvider");
        assertTrue(content.contains("calculate"), "Should have calculate method");
    }

    @Test
    @DisplayName("Scaffold analytics generates valid AnalyticsProvider")
    void scaffoldAnalyticsGeneratesValidSource() throws IOException {
        var cmd = new CliScaffoldCommand.AnalyticsSubcommand();
        setField(cmd, "name", "SectorRotation");
        setField(cmd, "pkg", "com.test.analytics");
        setField(cmd, "outputDir", tempDir.toString());

        Integer exitCode = cmd.call();
        assertEquals(0, exitCode);

        Path generated = tempDir.resolve("com/test/analytics/SectorRotation.java");
        assertTrue(Files.exists(generated));

        String content = Files.readString(generated);
        assertTrue(content.contains("implements AnalyticsProvider"), "Should implement AnalyticsProvider");
        assertTrue(content.contains("capabilities"), "Should have capabilities method");
    }

    /** Reflective field setter for picocli @Option fields. */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
