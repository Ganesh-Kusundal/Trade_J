package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CommandSuggesterTest {

    private static final List<String> COMMANDS = List.of(
            "quote", "depth", "ltp", "ohlc", "candles", "chain", "positions",
            "orders", "balance", "holdings", "backtest", "replay", "scan",
            "health", "exit", "help", "status", "broker", "portfolio"
    );

    @Test
    void exactMatch_returnsCommand() {
        List<String> suggestions = CommandSuggester.suggest("quote", COMMANDS);
        assertTrue(suggestions.contains("quote"));
    }

    @Test
    void oneCharTypo_suggestsCorrectCommand() {
        List<String> suggestions = CommandSuggester.suggest("qoute", COMMANDS);
        assertTrue(suggestions.contains("quote"), "Should suggest 'quote' for 'qoute'");
    }

    @Test
    void transposedChars_suggestsCorrectCommand() {
        List<String> suggestions = CommandSuggester.suggest("depht", COMMANDS);
        assertTrue(suggestions.contains("depth"), "Should suggest 'depth' for 'depht'");
    }

    @Test
    void missingChar_suggestsCorrectCommand() {
        List<String> suggestions = CommandSuggester.suggest("candle", COMMANDS);
        assertTrue(suggestions.contains("candles"), "Should suggest 'candles' for 'candle'");
    }

    @Test
    void completelyWrong_returnsEmptyOrFew() {
        List<String> suggestions = CommandSuggester.suggest("xyzzy", COMMANDS);
        assertTrue(suggestions.size() <= 3);
    }

    @Test
    void nullInput_returnsEmpty() {
        assertTrue(CommandSuggester.suggest(null, COMMANDS).isEmpty());
    }

    @Test
    void emptyInput_returnsEmpty() {
        assertTrue(CommandSuggester.suggest("", COMMANDS).isEmpty());
    }

    @Test
    void formatSuggestion_containsDidYouMean() {
        String msg = CommandSuggester.formatSuggestion("qoute", COMMANDS);
        assertTrue(msg.contains("Did you mean"), "Should contain 'Did you mean'");
        assertTrue(msg.contains("quote"), "Should contain the suggestion");
    }

    @Test
    void formatSuggestion_unknownCommand_noMatch() {
        String msg = CommandSuggester.formatSuggestion("xyzzy", COMMANDS);
        assertTrue(msg.contains("Unknown command"));
    }

    @Test
    void levenshteinDistance_sameStrings_isZero() {
        assertEquals(0, CommandSuggester.levenshteinDistance("hello", "hello"));
    }

    @Test
    void levenshteinDistance_oneEdit_isOne() {
        assertEquals(1, CommandSuggester.levenshteinDistance("quote", "quot"));
    }

    @Test
    void levenshteinDistance_transposition_isTwo() {
        assertEquals(2, CommandSuggester.levenshteinDistance("quote", "qoute"));
    }

    @Test
    void maxSuggestions_limitedToThree() {
        List<String> similar = List.of("aaa", "aab", "aac", "aad", "aae");
        List<String> suggestions = CommandSuggester.suggest("aaa", similar);
        assertTrue(suggestions.size() <= 3);
    }
}
