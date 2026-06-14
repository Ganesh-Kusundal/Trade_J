package com.tradej.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@link AuthController}. Uses MockMvc
 * with a real {@link SessionStore} and a real
 * {@link BrokerSignedUrlService} (no Spring context).
 *
 * <p>Exercises the full server-side auth surface: login,
 * whoami, broker-session, ws-url, logout. The end-to-end
 * curl checks in T22, T23, and T29 verified the same
 * surface; this test makes it part of the CI unit-tests
 * job.
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() {
        SessionStore store = new SessionStore();
        BrokerSignedUrlService signedUrlService = new BrokerSignedUrlService(
                store, "test-secret-not-for-prod", 30_000L);
        AuthController controller = new AuthController(store, signedUrlService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void loginMintsSessionAndReturnsId() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "broker", "DHAN",
                "accessToken", "tok-1",
                "clientId", "cid-1"
        ));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.broker").value("DHAN"))
                .andExpect(jsonPath("$.issuedAtMs").exists())
                .andExpect(jsonPath("$.expiresAtMs").exists());
    }

    @Test
    void whoamiWithValidSessionReturns200() throws Exception {
        // Mint a session first
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "DHAN", "accessToken", "tok", "clientId", "cid"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        mockMvc.perform(get("/api/v1/auth/whoami").header("X-Session-Id", sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sid))
                .andExpect(jsonPath("$.broker").value("DHAN"));
    }

    @Test
    void whoamiWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whoamiWithBadSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/whoami").header("X-Session-Id", "not-a-real-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "DHAN", "accessToken", "tok"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout").header("X-Session-Id", sid))
                .andExpect(status().isNoContent());

        // whoami after logout should 401
        mockMvc.perform(get("/api/v1/auth/whoami").header("X-Session-Id", sid))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void brokerSessionReturnsCredentialForValidSession() throws Exception {
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "DHAN", "accessToken", "secret-tok", "clientId", "secret-cid"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        mockMvc.perform(get("/api/v1/auth/broker-session").header("X-Session-Id", sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.broker").value("DHAN"))
                .andExpect(jsonPath("$.accessToken").value("secret-tok"))
                .andExpect(jsonPath("$.clientId").value("secret-cid"));
    }

    @Test
    void brokerSessionWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/broker-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wsUrlReturnsSignedUrlForValidSession() throws Exception {
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "DHAN", "accessToken", "tok", "clientId", "cid"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        mockMvc.perform(get("/api/v1/auth/ws-url")
                        .param("broker", "DHAN")
                        .param("instruments", "2885,11536")
                        .header("X-Session-Id", sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.broker").value("DHAN"))
                .andExpect(jsonPath("$.expiresAtMs").exists())
                .andExpect(jsonPath("$.signature").exists())
                .andExpect(jsonPath("$.ttlMs").value(30000));
    }

    @Test
    void wsUrlWithoutSessionReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/ws-url").param("broker", "DHAN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wsUrlWithUnsupportedBrokerReturns400() throws Exception {
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "DHAN", "accessToken", "tok"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        // BINANCE has no WebSocket URL; the service rejects it
        mockMvc.perform(get("/api/v1/auth/ws-url")
                        .param("broker", "BINANCE")
                        .header("X-Session-Id", sid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullLifecycleLoginWhoamiLogout() throws Exception {
        // 1. Login
        String loginBody = json.writeValueAsString(Map.of(
                "broker", "UPSTOX", "accessToken", "up-tok", "clientId", "up-cid"
        ));
        String loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.broker").value("UPSTOX"))
                .andReturn().getResponse().getContentAsString();
        String sid = json.readTree(loginRes).get("sessionId").asText();

        // 2. whoami (verify)
        mockMvc.perform(get("/api/v1/auth/whoami").header("X-Session-Id", sid))
                .andExpect(status().isOk());

        // 3. ws-url (use session for a signed URL)
        mockMvc.perform(get("/api/v1/auth/ws-url")
                        .param("broker", "UPSTOX")
                        .header("X-Session-Id", sid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.broker").value("UPSTOX"));

        // 4. Logout
        mockMvc.perform(post("/api/v1/auth/logout").header("X-Session-Id", sid))
                .andExpect(status().isNoContent());

        // 5. whoami after logout → 401
        mockMvc.perform(get("/api/v1/auth/whoami").header("X-Session-Id", sid))
                .andExpect(status().isUnauthorized());
    }
}
