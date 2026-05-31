package com.tradej.app.api;

import com.tradej.app.studio.StudioChartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("contract")
@ExtendWith(MockitoExtension.class)
class StudioControllerContractTest {

    @Mock
    private StudioChartService studioChartService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StudioController(studioChartService)).build();
    }

    @Test
    void startupCandidatesExposeScanMetadata() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scanDate", "2026-05-29");
        payload.put("scanTime", "14:45:00");
        payload.put("requestedScanTime", "09:45:00");
        payload.put("chartLookbackDays", 20);
        payload.put("selectionMode", "baseline");
        payload.put("provenance", Map.of("requestedScanTime", "09:45:00"));
        payload.put("candidates", List.of());

        when(studioChartService.startupCandidates(any(), anyInt())).thenReturn(payload);

        mockMvc.perform(get("/api/v1/studio/startup-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanDate").value("2026-05-29"))
                .andExpect(jsonPath("$.requestedScanTime").value("09:45:00"))
                .andExpect(jsonPath("$.chartLookbackDays").value(20));
    }
}
