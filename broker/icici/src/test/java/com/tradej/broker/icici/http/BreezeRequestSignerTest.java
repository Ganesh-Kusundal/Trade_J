package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeRequestSignerTest {

    @Test
    void timestampTruncatesToZeroMillisecondsLikePythonSdk() {
        Instant instant = Instant.parse("2024-06-01T10:23:56.789Z");
        assertEquals("2024-06-01T10:23:56.000Z", BreezeRequestSigner.timestamp(instant));
    }

    @Test
    void checksumMatchesPythonSdkGoldenVector() {
        String timestamp = "2024-06-01T10:23:56.000Z";
        String body = "{}";
        String secret = "secret_key_value";
        assertEquals(
                "80bc95891097cc79e05f2f1d1848c04dd635221c4432312674d65f7751c63961",
                BreezeRequestSigner.checksum(timestamp, body, secret)
        );
    }

    @Test
    void checksumMatchesPythonSdkOrderPayload() {
        String timestamp = "2024-06-01T10:23:56.000Z";
        String body = "{\"stock_code\":\"ITC\",\"exchange_code\":\"NSE\",\"product\":\"cash\",\"action\":\"buy\","
                + "\"order_type\":\"limit\",\"quantity\":\"1\",\"price\":\"263.15\",\"validity\":\"ioc\"}";
        String secret = "secret_key_value";
        assertEquals(
                "729b92c8aaf428d909ecb698ef051ed72fb85685b6289177df2715a1b23cf1c2",
                BreezeRequestSigner.checksum(timestamp, body, secret)
        );
    }

    @Test
    void emptyJsonBodyIsCompactObject() {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("{}", BreezeRequestSigner.jsonBody(mapper, mapper.createObjectNode()));
        assertEquals("{}", BreezeRequestSigner.jsonBody(mapper, null));
    }

    @Test
    void checksumHeaderPrefixMatchesPythonSdk() {
        String header = BreezeRequestSigner.checksumHeaderValue(
                "2024-06-01T10:23:56.000Z", "{}", "secret_key_value");
        assertEquals(
                "token 80bc95891097cc79e05f2f1d1848c04dd635221c4432312674d65f7751c63961",
                header
        );
    }
}
