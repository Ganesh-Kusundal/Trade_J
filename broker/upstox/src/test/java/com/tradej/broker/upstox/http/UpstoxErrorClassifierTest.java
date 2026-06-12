package com.tradej.broker.upstox.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxErrorClassifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void staticIpBlockedIsTypedException() throws Exception {
        var body = MAPPER.readTree("""
                {"status":"error","errors":[{"errorCode":"UDAPI1154",
                "message":"Access to this API is blocked due to static IP restrictions."}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.StaticIpBlocked.class, ex);
        assertEquals("UDAPI1154", ex.errorCode());
        assertEquals(403, ex.httpStatus());
    }

    @Test
    void marketOrderBlockedIsTypedException() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI1158",
                "message":"Market orders are not allowed. Try placing orders using the Market Protection feature instead."}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.MarketOrderBlocked.class, ex);
        assertEquals(400, ex.httpStatus());
    }

    @Test
    void algoNameIsTypedException() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI1156","message":"Invalid Algo name passed in headers"}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.InvalidAlgoName.class, ex);
    }

    @Test
    void orderNotFoundIsTyped() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI100010","message":"Order not found"}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.OrderNotFound.class, ex);
        assertEquals(404, ex.httpStatus());
    }

    @Test
    void disclosedQuantityTooSmallIsTyped() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI1039","message":"Disclosed quantity should not be less than 10%"}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.DisclosedQuantityTooSmall.class, ex);
    }

    @Test
    void marketProtectionOutOfRangeIsTyped() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI1159","message":"Market Protection cannot be greater than 25%"}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.MarketProtectionOutOfRange.class, ex);
    }

    @Test
    void uplinkBusinessRequiredIsTyped() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI100049","message":"Use Uplink Business"}]}
                """);
        var ex = UpstoxErrorClassifier.classify(body);
        assertNotNull(ex);
        assertInstanceOf(UpstoxApiException.UplinkBusinessRequired.class, ex);
    }

    @Test
    void unknownErrorCodeReturnsNullSoCallerCanUseGenericException() throws Exception {
        // Classifier returns null for unrecognised codes so the caller can
        // construct a generic UpstoxApiException with the right HTTP
        // status (the classifier does not know the status).
        var body = MAPPER.readTree("""
                {"errors":[{"errorCode":"UDAPI999999","message":"something custom"}]}
                """);
        assertNull(UpstoxErrorClassifier.classify(body));
    }

    @Test
    void noErrorsArrayReturnsNull() throws Exception {
        var body = MAPPER.readTree("""
                {"status":"success","data":[]}
                """);
        assertNull(UpstoxErrorClassifier.classify(body));
    }

    @Test
    void emptyErrorsArrayReturnsNull() throws Exception {
        var body = MAPPER.readTree("""
                {"status":"error","errors":[]}
                """);
        assertNull(UpstoxErrorClassifier.classify(body));
    }

    @Test
    void nonObjectReturnsNull() throws Exception {
        var body = MAPPER.readTree("[1,2,3]");
        assertNull(UpstoxErrorClassifier.classify(body));
    }

    @Test
    void missingErrorCodeReturnsNull() throws Exception {
        var body = MAPPER.readTree("""
                {"errors":[{"message":"no code"}]}
                """);
        assertNull(UpstoxErrorClassifier.classify(body));
    }

    @Test
    void allDocumentedCodesAreMapped() throws Exception {
        // Regression: every UDAPI code we documented must have a typed
        // exception; if a new UDAPI code is added, this test forces the
        // classifier to be updated.
        String[] codes = {
                "UDAPI1004", "UDAPI1007", "UDAPI100049", "UDAPI100010",
                "UDAPI100040", "UDAPI100041", "UDAPI1023", "UDAPI1010",
                "UDAPI1036", "UDAPI1029", "UDAPI1030", "UDAPI1031",
                "UDAPI1032", "UDAPI1039", "UDAPI1003", "UDAPI1055",
                "UDAPI1056", "UDAPI1008", "UDAPI1154", "UDAPI1156",
                "UDAPI1158", "UDAPI1159", "UDAPI1160", "UDAPI1161"
        };
        for (String code : codes) {
            var body = MAPPER.readTree("""
                    {"errors":[{"errorCode":"%s","message":"x"}]}
                    """.formatted(code));
            var ex = UpstoxErrorClassifier.classify(body);
            assertNotNull(ex, "no exception for " + code);
        }
    }
}
