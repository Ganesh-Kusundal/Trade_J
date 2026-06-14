package com.tradej.app.api;

import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("unit")
class RuntimeModeControllerTest {

    private RuntimeModeHolder holder;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        holder = new RuntimeModeHolder();
        holder.setMode(RuntimeMode.LIVE);
        mockMvc = MockMvcBuilders.standaloneSetup(new RuntimeModeController(holder)).build();
    }

    @Test
    void getMode_returnsCurrentMode() throws Exception {
        holder.setMode(RuntimeMode.PAPER);
        mockMvc.perform(get("/api/v1/runtime/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PAPER"))
                .andExpect(jsonPath("$.userToggleable").value(true));
    }

    @Test
    void getMode_returnsLiveByDefault() throws Exception {
        RuntimeModeHolder fresh = new RuntimeModeHolder();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RuntimeModeController(fresh)).build();
        mvc.perform(get("/api/v1/runtime/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"));
    }

    @Test
    void putMode_live_succeeds() throws Exception {
        holder.setMode(RuntimeMode.PAPER);
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"LIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"));
        assertEquals(RuntimeMode.LIVE, holder.mode());
    }

    @Test
    void putMode_paper_succeeds() throws Exception {
        holder.setMode(RuntimeMode.LIVE);
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PAPER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PAPER"));
        assertEquals(RuntimeMode.PAPER, holder.mode());
    }

    @Test
    void putMode_paper_caseInsensitive() throws Exception {
        holder.setMode(RuntimeMode.LIVE);
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"paper\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("PAPER"));
        assertEquals(RuntimeMode.PAPER, holder.mode());
    }

    @Test
    void putMode_replay_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"REPLAY\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(RuntimeMode.LIVE, holder.mode(),
                "REPLAY must not mutate the holder");
    }

    @Test
    void putMode_backtest_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"BACKTEST\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(RuntimeMode.LIVE, holder.mode(),
                "BACKTEST must not mutate the holder");
    }

    @Test
    void putMode_invalidMode_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"FOO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putMode_emptyBody_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putMode_blankMode_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/runtime/mode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
