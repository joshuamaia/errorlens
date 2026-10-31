package dev.errorlens.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.export.enabled=false")
class SimulatorApiTest {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { }).build();
    }

    @Test
    void controlEndpointsRequireToken() {
        var denied = client().post().uri("/internal/control/reset").retrieve().toBodilessEntity();
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void configuredBehaviorDrivesBusinessResponses() {
        var configured = client().put().uri("/internal/control/behaviors/test-key-0001")
                .header("X-Lab-Control-Token", "lab-control-token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mode", "ERROR_503", "delayMs", 0, "failures", 0, "status", 503))
                .retrieve().toBodilessEntity();
        assertThat(configured.getStatusCode().value()).isEqualTo(200);

        var failed = client().get().uri("/api/v1/customer-profile/1").header("X-Simulation-Key", "test-key-0001")
                .header("X-Correlation-Id", "corr-abcdef12").retrieve().toEntity(Map.class);
        assertThat(failed.getStatusCode().value()).isEqualTo(503);
        assertThat(failed.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-abcdef12");

        var ok = client().get().uri("/api/v1/customer-profile/1").retrieve().toEntity(Map.class);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(ok.getBody()).containsKey("segment");
    }
}
