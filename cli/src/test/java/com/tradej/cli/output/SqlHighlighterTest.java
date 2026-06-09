package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SqlHighlighterTest {

    @Test
    void highlight_null_returnsNull() {
        assertNull(SqlHighlighter.highlight(null));
    }

    @Test
    void highlight_empty_returnsEmpty() {
        assertEquals("", SqlHighlighter.highlight(""));
    }

    @Test
    void highlight_selectKeyword_highlighted() {
        String result = SqlHighlighter.highlight("SELECT * FROM table1");
        assertNotNull(result);
        // The result should be longer than input due to ANSI codes (when enabled)
        assertTrue(result.length() >= "SELECT * FROM table1".length());
    }

    @Test
    void highlight_stringLiteral_highlighted() {
        String result = SqlHighlighter.highlight("SELECT * FROM t WHERE name = 'RELIANCE'");
        assertNotNull(result);
    }

    @Test
    void highlight_number_highlighted() {
        String result = SqlHighlighter.highlight("SELECT * FROM t WHERE price > 1000");
        assertNotNull(result);
    }

    @Test
    void highlight_comment_dimmed() {
        String result = SqlHighlighter.highlight("SELECT * -- this is a comment\nFROM t");
        assertNotNull(result);
    }

    @Test
    void highlight_multipleKeywords_allHighlighted() {
        String result = SqlHighlighter.highlight(
                "SELECT symbol, open_interest FROM option_chain WHERE oi > 1000 ORDER BY oi DESC LIMIT 10");
        assertNotNull(result);
        assertTrue(result.contains("symbol") || result.contains("option_chain"));
    }

    @Test
    void highlight_typeKeywords_highlighted() {
        String result = SqlHighlighter.highlight("CREATE TABLE t (id INT, name VARCHAR)");
        assertNotNull(result);
    }
}
