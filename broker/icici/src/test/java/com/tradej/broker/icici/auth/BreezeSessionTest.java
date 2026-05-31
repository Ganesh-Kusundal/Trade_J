package com.tradej.broker.icici.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeSessionTest {

    @Test
    void headerSessionTokenMatchesPythonSdkReEncoding() {
        BreezeSession session = BreezeSession.fromUserAndKey("AH415667", "55795161", 0L, 1L);
        assertEquals(session.base64SessionToken(), session.headerSessionToken());
    }
}
