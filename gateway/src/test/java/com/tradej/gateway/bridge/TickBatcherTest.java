package com.tradej.gateway.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TickBatcherTest {

    @Mock private GatewayTopicRouter router;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TickBatcher batcher;

    @BeforeEach
    void setUp() {
        batcher = new TickBatcher(router, objectMapper, 50L, 100);
    }

    @AfterEach
    void tearDown() {
        batcher.close();
    }

    private ObjectNode tickPayload(String symbol, long ltp) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("symbol", symbol);
        node.put("ltpPaisa", ltp);
        return node;
    }

    @Test
    void add_buffersWithoutImmediateFlush() {
        batcher.add(tickPayload("RELIANCE", 250000L));
        batcher.add(tickPayload("TCS", 380000L));
        assertEquals(2, batcher.bufferSize());
        verify(router, never()).publish(any(), any(byte[].class));
    }

    @Test
    void flush_sendsBatchAsJsonArray() throws Exception {
        batcher.add(tickPayload("RELIANCE", 250000L));
        batcher.add(tickPayload("TCS", 380000L));
        batcher.flush();

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(router).publish(eq(GatewayTopic.MARKET_TICK), captor.capture());

        JsonNode array = objectMapper.readTree(captor.getValue());
        assertTrue(array.isArray());
        assertEquals(2, array.size());
        assertEquals("RELIANCE", array.get(0).get("symbol").asText());
        assertEquals("TCS", array.get(1).get("symbol").asText());
    }

    @Test
    void flush_emptyBuffer_doesNothing() {
        batcher.flush();
        verify(router, never()).publish(any(), any(byte[].class));
    }

    @Test
    void flush_clearsBufferAfterSend() {
        batcher.add(tickPayload("INFY", 150000L));
        batcher.flush();
        assertEquals(0, batcher.bufferSize());
    }

    @Test
    void maxSizeTriggerImmediateFlush() throws Exception {
        TickBatcher smallBatcher = new TickBatcher(router, objectMapper, 5000L, 3);
        smallBatcher.add(tickPayload("A", 100L));
        smallBatcher.add(tickPayload("B", 200L));
        smallBatcher.add(tickPayload("C", 300L)); // triggers flush at maxBatchSize=3

        verify(router).publish(eq(GatewayTopic.MARKET_TICK), any(byte[].class));
        assertEquals(0, smallBatcher.bufferSize());
        smallBatcher.close();
    }

    @Test
    void multipleFlushes_eachSendsSeparateBatch() throws Exception {
        batcher.add(tickPayload("A", 100L));
        batcher.flush();
        batcher.add(tickPayload("B", 200L));
        batcher.flush();

        verify(router, times(2)).publish(eq(GatewayTopic.MARKET_TICK), any(byte[].class));
    }
}
