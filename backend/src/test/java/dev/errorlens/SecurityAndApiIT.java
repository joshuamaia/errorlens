package dev.errorlens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import dev.errorlens.support.IntegrationTestBase;

/**
 * Segurança, Problem Details, correlationId e endpoints públicos.
 */
class SecurityAndApiIT extends IntegrationTestBase {

    @Test
    void diagnosticEndpointsRequireAuthentication() {
        var response = client().get().uri("/api/v1/executions").retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
        assertThat(response.getBody()).containsEntry("errorCode", "AUTHENTICATION_REQUIRED")
                .containsKey("correlationId").containsEntry("type", "about:blank");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
    }

    @Test
    void viewerCannotExecuteScenariosOrReadDiagnostics() {
        String viewer = login("viewer", "viewer-lab");
        assertThat(client().get().uri("/api/v1/scenarios").header(HttpHeaders.AUTHORIZATION, viewer).retrieve()
                .toBodilessEntity().getStatusCode().value()).isEqualTo(200);
        assertThat(client().post().uri("/api/v1/scenarios/A01/execute").header(HttpHeaders.AUTHORIZATION, viewer)
                .retrieve().toBodilessEntity().getStatusCode().value()).isEqualTo(403);
        assertThat(client().get().uri("/api/v1/executions").header(HttpHeaders.AUTHORIZATION, viewer).retrieve()
                .toBodilessEntity().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void invalidCredentialsReturnProblemDetails() {
        var response = client().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", "admin", "password", "errada")).retrieve().toEntity(Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("errorCode", "INVALID_CREDENTIALS");
    }

    @Test
    void correlationIdIsPropagatedOrGenerated() {
        var echoed = client().get().uri("/actuator/health").header("X-Correlation-Id", "test-correlation-0001")
                .retrieve().toBodilessEntity();
        assertThat(echoed.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("test-correlation-0001");
        var generated = client().get().uri("/actuator/health").header("X-Correlation-Id", "<script>")
                .retrieve().toBodilessEntity();
        assertThat(generated.getHeaders().getFirst("X-Correlation-Id")).hasSize(36).doesNotContain("<");
    }

    @Test
    void labEndpointsNeverExposeStackTraces() {
        var response = client().get().uri("/api/v1/lab/customers/999999")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + java.util.Base64.getEncoder()
                        .encodeToString("runner:runner-lab".getBytes()))
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("CUSTOMER_NOT_FOUND").doesNotContain("at dev.errorlens");
    }

    @Test
    void analyzerAcceptsStackTraceAndRejectsOversizedInput() {
        String token = adminToken();
        Map<?, ?> analysis = client().post().uri("/api/v1/analyzer/stacktrace").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", "java.lang.IllegalStateException: x\n\tat a.B.c(B.java:1)"))
                .retrieve().body(Map.class);
        assertThat(((Map<?, ?>) analysis.get("rootCause")).get("simpleName")).isEqualTo("IllegalStateException");

        var tooLarge = client().post().uri("/api/v1/analyzer/stacktrace").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("content", "x".repeat(600_000)))
                .retrieve().toEntity(Map.class);
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
    }
}
