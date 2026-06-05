package com.tradej.app.integration;

import com.tradej.app.pipeline.DagPipelineRuntimeService;
import com.tradej.app.pipeline.PipelineRuntimeService;
import com.tradej.app.studio.StudioChartService;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.registry.NodeRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies REST shapes used by the React console ({@code frontend/src/api/client.ts})
 * against real controller code (mocked services, no browser mocks).
 */
@Tag("integration")
@Tag("api")
@Tag("console")
@ActiveProfiles("console-api-contract")
@SpringBootTest(
        classes = ConsoleApiContractTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0"}
)
class ConsoleApiContractIntegrationTest extends AdminTestBase {

    @MockitoBean
    private StudioChartService studioChartService;

    @MockitoBean
    private PipelineRuntimeService pipelineRuntimeService;

    @MockitoBean
    private DagPipelineRuntimeService dagPipelineRuntimeService;

    @MockitoBean
    private NodeRegistry nodeRegistry;

    @MockitoBean
    private OrderCommand orderCommandPort;

    @SuppressWarnings("unchecked")
    @Test
    void actuatorHealthReturnsStatus() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsKey("status");
    }

    @SuppressWarnings("unchecked")
    @Test
    void strategiesReturnsPluginsObjectNotArray() {
        when(strategyEngine.pluginNames()).thenReturn(List.of("plugin-a", "plugin-b"));

        ResponseEntity<Map> response = rest.getForEntity("/admin/strategies", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("pluginCount")).isEqualTo(2);
        assertThat(body.get("plugins")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> plugins = (List<String>) body.get("plugins");
        assertThat(plugins).containsExactly("plugin-a", "plugin-b");
    }

    @SuppressWarnings("unchecked")
    @Test
    void killSwitchReturnsEnabledAndAcknowledged() {
        when(brokerConnection.orders()).thenReturn(orderCommandPort);
        when(orderCommandPort.setKillSwitch(true)).thenReturn(true);

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/risk/kill-switch/true", null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("enabled", true);
        assertThat(body).containsEntry("acknowledged", true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void studioStartupCandidatesReturnsScanMetadata() {
        when(studioChartService.startupCandidates(any(Optional.class), eq(3)))
                .thenReturn(Map.of(
                        "scanDate", "2026-05-26",
                        "candidates", List.of(),
                        "selectionMode", "baseline"
                ));

        ResponseEntity<Map> response = rest.getForEntity(
                "/api/v1/studio/startup-candidates?topN=3", Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("scanDate", "2026-05-26");
    }

    @SuppressWarnings("unchecked")
    @Test
    void pipelineGraphAndCategoriesAreReachable() {
        when(nodeRegistry.categories()).thenReturn(List.of("input", "output"));
        when(pipelineRuntimeService.activeGraph()).thenReturn(sampleGraph());

        ResponseEntity<Map> graph = rest.getForEntity("/api/v1/pipeline/graph", Map.class);
        assertThat(graph.getStatusCode().value()).isEqualTo(200);
        assertThat(graph.getBody()).containsEntry("id", "test-graph");

        ResponseEntity<List> categories = rest.getForEntity(
                "/api/v1/pipeline/node-types/categories", List.class);
        assertThat(categories.getStatusCode().value()).isEqualTo(200);
        assertThat(categories.getBody()).containsExactly("input", "output");
    }

    private static PipelineGraph sampleGraph() {
        return new PipelineGraph(
                "test-graph",
                "Test",
                1,
                List.of(),
                List.of(),
                PipelineExecutionMode.HOT_PATH
        );
    }
}
