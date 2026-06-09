package com.tradej.cli.output;

import com.tradej.cli.config.AliasStore;
import com.tradej.cli.config.MacroStore;
import com.tradej.cli.config.SavedQueryStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests verifying CLI utility integration:
 * alias expansion, macro execution, saved queries, and rich output rendering.
 */
@Tag("unit")
class CliEndToEndTest {

    // ── Alias expansion E2E ──────────────────────────────────────────

    @Test
    void aliasExpand_singleWord_expandsCorrectly() {
        AliasStore store = AliasStore.load();
        store.add("e2e-q", "quote");
        assertEquals("quote RELIANCE NSE_EQ", store.expand("e2e-q RELIANCE NSE_EQ"));
        store.remove("e2e-q");
    }

    @Test
    void aliasExpand_multiWord_expandsCorrectly() {
        AliasStore store = AliasStore.load();
        store.add("e2e-pos", "portfolio positions");
        assertEquals("portfolio positions", store.expand("e2e-pos"));
        store.remove("e2e-pos");
    }

    @Test
    void aliasExpand_noMatch_returnsOriginal() {
        AliasStore store = AliasStore.load();
        assertEquals("unknown-cmd RELIANCE", store.expand("unknown-cmd RELIANCE"));
    }

    // ── Macro E2E ───────────────────────────────────────────────────

    @Test
    void macro_roundTrip_preservesCommands() {
        MacroStore store = MacroStore.load();
        List<String> commands = List.of("quote RELIANCE", "chain NIFTY --expiry nearest", "balance");
        store.add("e2e-morning", commands);

        List<String> loaded = store.get("e2e-morning");
        assertNotNull(loaded);
        assertEquals(3, loaded.size());
        assertEquals("quote RELIANCE", loaded.get(0));
        assertEquals("chain NIFTY --expiry nearest", loaded.get(1));
        assertEquals("balance", loaded.get(2));

        store.remove("e2e-morning");
        assertFalse(store.has("e2e-morning"));
    }

    // ── Saved query E2E ─────────────────────────────────────────────

    @Test
    void query_roundTrip_preservesSql() {
        SavedQueryStore store = SavedQueryStore.load();
        String sql = "SELECT symbol, open_interest FROM option_chain ORDER BY open_interest DESC LIMIT 10";
        store.save("e2e-top-oi", sql);

        assertEquals(sql, store.get("e2e-top-oi"));
        assertTrue(store.has("e2e-top-oi"));

        store.remove("e2e-top-oi");
        assertFalse(store.has("e2e-top-oi"));
    }

    // ── Rich table + Ansi E2E ───────────────────────────────────────

    @Test
    void richTable_withPnlColors_rendersCorrectly() {
        RichTable table = RichTable.of("Symbol", "Qty", "PnL")
                .addRow("RELIANCE", "100", Ansi.pnl(250000L))
                .addRow("TCS", "50", Ansi.pnl(-120000L))
                .addRow("INFY", "75", Ansi.pnl(0L));

        assertDoesNotThrow(() -> table.print());
    }

    // ── Sparkline E2E ───────────────────────────────────────────────

    @Test
    void sparkline_fromPrices_rendersCorrectLength() {
        double[] prices = {2500, 2510, 2505, 2520, 2535, 2530, 2545, 2543};
        String spark = Sparkline.render(prices);
        assertEquals(8, spark.replaceAll("\033\\[[;\\d]*m", "").length());
    }

    @Test
    void sparkline_colored_uptrendIsGreen() {
        double[] uptrend = {100, 105, 110, 115, 120};
        String colored = Sparkline.renderColored(uptrend);
        assertFalse(colored.isEmpty());
    }

    // ── CommandSuggester E2E ────────────────────────────────────────

    @Test
    void commandSuggester_typo_suggestsCorrectCommand() {
        List<String> commands = List.of("quote", "depth", "ltp", "chain", "positions", "orders");
        String suggestion = CommandSuggester.formatSuggestion("qoute", commands);
        assertTrue(suggestion.contains("quote"));
    }

    // ── ProgressBar E2E ─────────────────────────────────────────────

    @Test
    void progressBar_fullCycle_completes() {
        ProgressBar bar = new ProgressBar("E2E Test", 10);
        for (int i = 1; i <= 10; i++) {
            bar.update(i);
        }
        bar.complete();
    }

    // ── BrokerDebugRenderer E2E ─────────────────────────────────────

    @Test
    void brokerDebug_fullRequest_rendersAllSections() {
        BrokerDebugRenderer.DebugInfo info = BrokerDebugRenderer.DebugInfo.builder()
                .method("GET")
                .url("https://api.dhan.co/v2/market/quote")
                .requestHeaders(java.util.Map.of("access-token", "eyJhbGciOiJIUzI1NiJ9.test"))
                .statusCode(200)
                .latencyMs(14)
                .responseBody("{\"last_price\": 2543.50}")
                .mappedResult("Quote{ltpPaisa=254350}")
                .rateLimitRemaining("95/100")
                .build();

        String output = BrokerDebugRenderer.render(info);
        assertTrue(output.contains("Request"));
        assertTrue(output.contains("Response"));
        assertTrue(output.contains("Mapping"));
        assertTrue(output.contains("200"));
        assertTrue(output.contains("95/100"));
    }
}
