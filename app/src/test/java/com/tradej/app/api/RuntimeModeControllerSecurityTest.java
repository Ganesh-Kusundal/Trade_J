package com.tradej.app.api;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.app.security.JwtTokenService;
import com.tradej.test.security.RuntimeModeControllerSecurityTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code RuntimeModeController} is protected by the G1 JWT
 * auth filter chain. The endpoint is not in the SecurityConfiguration
 * whitelist, so requests without a valid Bearer token must return 401.
 *
 * <p>Uses a top-level {@link RuntimeModeControllerSecurityTestContext}
 * that lives in a non-scanned package so the full application context
 * (started by other tests with {@code @ComponentScan("com.tradej")}) does
 * not auto-discover it. {@code @DirtiesContext} keeps the context cache
 * isolated for the same reason.
 */
@Tag("unit")
@SpringBootTest(
        classes = RuntimeModeControllerSecurityTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimeModeControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RuntimeModeHolder holder;

    @Autowired
    private JwtTokenService tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        holder.setMode(RuntimeMode.LIVE);
    }

    @Test
    void getMode_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/runtime/mode"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void putMode_withoutAuth_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PAPER\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMode_withValidBearerToken_succeeds() throws Exception {
        String token = tokenService.generateToken("admin");
        mockMvc.perform(get("/api/v1/runtime/mode")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"));
    }

    @Test
    void putMode_withValidBearerToken_succeeds() throws Exception {
        String token = tokenService.generateToken("admin");
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PAPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PAPER"));
    }

    @Test
    void putMode_withInvalidBearerToken_returns401() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .header("Authorization", "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PAPER\"}"))
                .andExpect(status().isUnauthorized());
    }
}
