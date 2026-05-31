package com.tradej.broker.icici.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BreezeApiSessionUrlParserTest {

    @Test
    void parsesApisessionQueryParam() {
        String session = BreezeApiSessionUrlParser.parseApiSession(
                "http://127.0.0.1:9080/api?apisession=ABC123XYZ");
        assertEquals("ABC123XYZ", session);
    }

    @Test
    void parsesApiSessionUnderscoreQueryParam() {
        String session = BreezeApiSessionUrlParser.parseApiSession(
                "https://127.0.0.1:9080/api?API_Session=token%2Bvalue");
        assertEquals("token+value", session);
    }

    @Test
    void parsesIciciDirectRedirectUrl() {
        String session = BreezeApiSessionUrlParser.parseApiSession(
                "https://api.icicidirect.com/?apisession=55795104");
        assertEquals("55795104", session);
    }

    @Test
    void parsesApisessionEmbeddedInHtml() {
        String session = BreezeApiSessionUrlParser.parseApiSessionFromText(
                "<script>window.location='https://api.icicidirect.com/?apisession=55795104';</script>");
        assertEquals("55795104", session);
    }

    @Test
    void returnsNullWhenMissing() {
        assertNull(BreezeApiSessionUrlParser.parseApiSession("http://127.0.0.1:9080/api?other=1"));
    }
}
