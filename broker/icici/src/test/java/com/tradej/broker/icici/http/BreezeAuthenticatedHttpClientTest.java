package com.tradej.broker.icici.http;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeAuthenticatedHttpClientTest {

    @Test
    void checksumUsesRequestSignerFormat() {
        String timestamp = "2024-06-01T10:23:56.000Z";
        String body = "{}";
        String secret = "secret_key_value";
        assertEquals(
                "80bc95891097cc79e05f2f1d1848c04dd635221c4432312674d65f7751c63961",
                BreezeRequestSigner.checksum(timestamp, body, secret)
        );
    }
}
