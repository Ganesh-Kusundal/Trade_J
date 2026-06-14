package com.tradej.broker.dhan.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the strict schema behavior of {@link DhanTokenStateStore}.
 * (LOW-1 fix: unknown fields now fail loudly instead of being silently dropped.)
 */
@Tag("unit")
class DhanTokenStateStoreSchemaTest {

    @Test
    void loadRejectsStateFileWithUnknownField(@TempDir Path temp) throws Exception {
        Path stateFile = temp.resolve("token-state.json");
        // Hand-craft a state file with an extra "refreshToken" field that
        // DhanTokenState doesn't know about.
        Files.writeString(stateFile, "{\n" +
                "  \"accessToken\" : \"abc\",\n" +
                "  \"expiryEpochMs\" : 9999999999,\n" +
                "  \"issuedAtEpochMs\" : 1000,\n" +
                "  \"source\" : \"STATIC\",\n" +
                "  \"refreshToken\" : \"rt-1234\"\n" +
                "}\n");
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile);
        IllegalStateException ex = assertThrows(IllegalStateException.class, store::load);
        assertTrue(ex.getMessage().contains("refreshToken") || ex.getMessage().contains("unrecognized"),
                "error must mention the unknown field; was: " + ex.getMessage());
    }

    @Test
    void loadRejectsCorruptStateWithBlankAccessToken(@TempDir Path temp) throws Exception {
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile, "{\n" +
                "  \"accessToken\" : \"\",\n" +
                "  \"expiryEpochMs\" : 9999999999,\n" +
                "  \"issuedAtEpochMs\" : 1000,\n" +
                "  \"source\" : \"STATIC\"\n" +
                "}\n");
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile);
        IllegalStateException ex = assertThrows(IllegalStateException.class, store::load);
        assertTrue(ex.getMessage().toLowerCase().contains("corrupt"),
                "error must mention corrupt state; was: " + ex.getMessage());
    }

    @Test
    void loadAcceptsValidStateFile(@TempDir Path temp) throws Exception {
        Path stateFile = temp.resolve("token-state.json");
        Files.writeString(stateFile, "{\n" +
                "  \"accessToken\" : \"abc\",\n" +
                "  \"expiryEpochMs\" : 9999999999,\n" +
                "  \"issuedAtEpochMs\" : 1000,\n" +
                "  \"source\" : \"STATIC\"\n" +
                "}\n");
        DhanTokenStateStore store = new DhanTokenStateStore(stateFile);
        DhanTokenState state = store.load().orElseThrow();
        assertEquals("abc", state.accessToken());
        assertEquals(9999999999L, state.expiryEpochMs());
        assertEquals(1000L, state.issuedAtEpochMs());
        assertEquals("STATIC", state.source());
    }
}
