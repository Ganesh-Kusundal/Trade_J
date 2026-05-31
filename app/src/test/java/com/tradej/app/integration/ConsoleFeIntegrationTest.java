package com.tradej.app.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke: embedded server serves the built console SPA from classpath static resources.
 */
@Tag("integration")
@Tag("api")
@Tag("console")
@SpringBootTest(
        classes = ConsoleFeTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.port=0"}
)
class ConsoleFeIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void servesConsoleIndexHtml() {
        ResponseEntity<String> response = rest.getForEntity("/console/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Trade-J");
    }

    @Test
    void rootServesConsoleAfterRedirect() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Trade-J");
    }

    @Test
    void actuatorHealthReachableFromSameHost() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("status");
    }
}
