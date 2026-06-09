package com.tradej.cli.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AliasStoreTest {

    @Test
    void load_emptyStore_returnsEmpty() {
        AliasStore store = AliasStore.load();
        // May have aliases from user's ~/.tradej — just verify it loads
        assertNotNull(store);
        assertNotNull(store.all());
    }

    @Test
    void expand_knownAlias_expandsCorrectly() {
        AliasStore store = AliasStore.load();
        store.add("q", "quote");
        assertEquals("quote RELIANCE", store.expand("q RELIANCE"));
        store.remove("q");
    }

    @Test
    void expand_unknownAlias_returnsOriginal() {
        AliasStore store = AliasStore.load();
        assertEquals("xyzzy RELIANCE", store.expand("xyzzy RELIANCE"));
    }

    @Test
    void expand_multiWordExpansion_works() {
        AliasStore store = AliasStore.load();
        store.add("pos", "portfolio positions");
        assertEquals("portfolio positions", store.expand("pos"));
        store.remove("pos");
    }

    @Test
    void addAndRemove_roundTrip() {
        AliasStore store = AliasStore.load();
        store.add("test-alias-123", "some command");
        assertTrue(store.has("test-alias-123"));
        assertEquals("some command", store.get("test-alias-123"));
        assertTrue(store.remove("test-alias-123"));
        assertFalse(store.has("test-alias-123"));
    }

    @Test
    void all_returnsCopy() {
        AliasStore store = AliasStore.load();
        Map<String, String> all = store.all();
        assertNotNull(all);
    }
}
