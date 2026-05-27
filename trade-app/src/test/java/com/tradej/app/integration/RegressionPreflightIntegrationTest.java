package com.tradej.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("integration")
@Tag("regression-preflight")
class RegressionPreflightIntegrationTest {
    @Test
    void liveProfilePreflightsFundLimit() {
        Path liveProps = LiveDhanTestSupport.propertiesPathForTest("config/dhan-local.properties");
        if (!Files.exists(liveProps)) {
            fail("Missing live credentials file: " + liveProps + " — copy config/dhan-local.properties.example");
        }
        String clientId = LiveDhanTestSupport.value("DHAN_CLIENT_ID", "dhan.clientId");
        String accessToken = LiveDhanTestSupport.value("DHAN_ACCESS_TOKEN", "dhan.accessToken");
        if (!LiveDhanTestSupport.isPresent(clientId) || !LiveDhanTestSupport.isPresent(accessToken)) {
            fail("Live profile incomplete: set dhan.clientId and dhan.accessToken in " + liveProps);
        }
        assertTrue(preflight(clientId, accessToken, "https://api.dhan.co/v2/fundlimit"),
                "Live preflight failed for api.dhan.co/v2/fundlimit — token may be expired.");
    }

    @Test
    void sandboxProfilePreflightsFundLimit() {
        Path sandboxProps = LiveDhanTestSupport.propertiesPathForTest("config/dhan-sandbox.properties");
        if (!Files.exists(sandboxProps)) {
            fail("Missing sandbox credentials file: " + sandboxProps + " — copy config/dhan-sandbox.properties.example");
        }
        String clientId = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_SANDBOX_CLIENT_ID", "dhan.sandbox.clientId", "2505162156");
        String accessToken = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_SANDBOX_ACCESS_TOKEN", "dhan.sandbox.accessToken", null);
        String baseUrl = LiveDhanTestSupport.valueForProfile(LiveDhanTestSupport.Profile.SANDBOX, "DHAN_SANDBOX_REST_BASE_URL", "dhan.sandbox.restBaseUrl", "https://sandbox.dhan.co/v2");
        if (!LiveDhanTestSupport.isPresent(accessToken)) {
            fail("Sandbox profile incomplete: set dhan.sandbox.accessToken in " + sandboxProps);
        }
        assertTrue(preflight(clientId, accessToken, baseUrl + "/fundlimit"),
                "Sandbox preflight failed for sandbox.dhan.co/v2/fundlimit — token may be expired.");
    }

    private static boolean preflight(String clientId, String accessToken, String url) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .header("Accept", "application/json")
                    .header("client-id", clientId)
                    .header("access-token", accessToken)
                    .GET()
                    .build();
            var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("dhanClientId");
        } catch (Exception ex) {
            return false;
        }
    }
}
