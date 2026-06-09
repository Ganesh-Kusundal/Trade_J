package com.tradej.cli.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MacroStoreTest {

    @Test
    void load_returnsNonNull() {
        MacroStore store = MacroStore.load();
        assertNotNull(store);
        assertNotNull(store.all());
    }

    @Test
    void addAndGet_roundTrip() {
        MacroStore store = MacroStore.load();
        store.add("test-macro-xyz", List.of("quote RELIANCE", "balance"));
        assertTrue(store.has("test-macro-xyz"));
        List<String> cmds = store.get("test-macro-xyz");
        assertNotNull(cmds);
        assertEquals(2, cmds.size());
        assertEquals("quote RELIANCE", cmds.get(0));
        assertEquals("balance", cmds.get(1));
        assertTrue(store.remove("test-macro-xyz"));
        assertFalse(store.has("test-macro-xyz"));
    }

    @Test
    void get_nonExistent_returnsNull() {
        MacroStore store = MacroStore.load();
        assertNull(store.get("nonexistent-macro-abc"));
    }

    @Test
    void remove_nonExistent_returnsFalse() {
        MacroStore store = MacroStore.load();
        assertFalse(store.remove("nonexistent-macro-abc"));
    }
}
