package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class UpstoxOptionChainRestClientTest {

    private final UpstoxJsonHttpClient httpClient = mock(UpstoxJsonHttpClient.class);
    private final UpstoxOptionChainRestClient client = new UpstoxOptionChainRestClient(httpClient);
    private final ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);

    {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode successNode = mapper.createObjectNode().put("status", "success");
        when(httpClient.getJson(anyString())).thenReturn(successNode);
    }

    @Test
    void getExpiries_encodesPipeCharacterInInstrumentKey() {
        client.getExpiries("NSE_INDEX|Nifty 50");

        verify(httpClient).getJson(pathCaptor.capture());
        String path = pathCaptor.getValue();
        assertFalse(path.contains("|"), "Pipe character must be URL-encoded: " + path);
        assertTrue(path.contains("NSE_INDEX%7CNifty"), "Pipe should be encoded as %7C: " + path);
    }

    @Test
    void getOptionChain_encodesBothParameters() {
        client.getOptionChain("NSE_INDEX|Nifty Bank", "2026-06-26");

        verify(httpClient).getJson(pathCaptor.capture());
        String path = pathCaptor.getValue();
        assertFalse(path.contains("|"), "Pipe must be encoded: " + path);
        assertTrue(path.contains("instrument_key=NSE_INDEX%7CNifty"));
        assertTrue(path.contains("expiry_date=2026-06-26"));
    }

    @Test
    void getOptionContracts_encodesInstrumentKey() {
        client.getOptionContracts("NSE_FO|12345");

        verify(httpClient).getJson(pathCaptor.capture());
        String path = pathCaptor.getValue();
        assertFalse(path.contains("|"), "Pipe must be encoded: " + path);
        assertTrue(path.contains("NSE_FO%7C12345"));
    }

    @Test
    void getGreeks_encodesOptionKeyWithMultiplePipes() {
        client.getGreeks("NSE_FO|CALL|25000|2026-06-26");

        verify(httpClient).getJson(pathCaptor.capture());
        String path = pathCaptor.getValue();
        assertFalse(path.contains("|"), "All pipes must be encoded: " + path);
        assertTrue(path.contains("NSE_FO%7CCALL%7C25000%7C2026-06-26"));
    }
}
