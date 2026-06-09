package com.tradej.cli.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SavedQueryStoreTest {

    @Test
    void load_returnsNonNull() {
        SavedQueryStore store = SavedQueryStore.load();
        assertNotNull(store);
        assertNotNull(store.all());
    }

    @Test
    void saveAndGet_roundTrip() {
        SavedQueryStore store = SavedQueryStore.load();
        store.save("test-query-xyz", "SELECT * FROM candles WHERE symbol = 'RELIANCE'");
        assertTrue(store.has("test-query-xyz"));
        assertEquals("SELECT * FROM candles WHERE symbol = 'RELIANCE'", store.get("test-query-xyz"));
        assertTrue(store.remove("test-query-xyz"));
        assertFalse(store.has("test-query-xyz"));
    }

    @Test
    void remove_nonExistent_returnsFalse() {
        SavedQueryStore store = SavedQueryStore.load();
        assertFalse(store.remove("nonexistent-query-abc"));
    }

    @Test
    void all_returnsCopy() {
        SavedQueryStore store = SavedQueryStore.load();
        Map<String, String> all = store.all();
        assertNotNull(all);
    }

    @Test
    void get_nonExistent_returnsNull() {
        SavedQueryStore store = SavedQueryStore.load();
        assertNull(store.get("definitely-not-saved-query"));
    }
}
