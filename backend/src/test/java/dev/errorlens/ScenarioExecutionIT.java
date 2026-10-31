package dev.errorlens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;

import dev.errorlens.support.IntegrationTestBase;

/**
 * Executa os cenários pelo endpoint real e valida status, exceptions, instrumentação,
 * persistência e diagnóstico.
 */
class ScenarioExecutionIT extends IntegrationTestBase {

    @ParameterizedTest(name = "{0} -> HTTP {1} ({2})")
    @CsvSource({
            "A01, 200, SUCCESS, ",
            "A02, 201, SUCCESS, ",
            "A03, 200, SUCCESS, ",
            "A04, 204, SUCCESS, ",
            "A05, 200, SUCCESS, ",
            "B01, 404, FAILURE, ResourceNotFoundException",
            "B02, 400, FAILURE, MethodArgumentNotValidException",
            "B03, 400, FAILURE, HttpMessageNotReadableException",
            "B04, 400, FAILURE, MethodArgumentTypeMismatchException",
            "B05, 405, FAILURE, HttpRequestMethodNotSupportedException",
            "C01, 401, FAILURE, InsufficientAuthenticationException",
            "C02, 403, FAILURE, AuthorizationDeniedException",
            "C03, 200, SUCCESS, ",
            "D01, 409, FAILURE, DataIntegrityViolationException",
            "D02, 409, FAILURE, DataIntegrityViolationException",
            "D03, 500, FAILURE, BadSqlGrammarException",
            "E01, 500, FAILURE, NullPointerException",
            "E02, 400, FAILURE, IllegalArgumentException",
            "E03, 422, FAILURE, InvoiceGenerationException"
    })
    void executesScenarioAgainstRealStack(String code, int status, String outcome, String exception) {
        Map<String, Object> detail = execute(code, "DEFAULT");
        Map<String, Object> summary = summary(detail);

        assertThat(summary.get("httpStatus")).isEqualTo(status);
        assertThat(summary.get("outcome")).isEqualTo(outcome);
        assertThat(summary.get("matchedExpectation")).isEqualTo(true);
        assertThat((String) summary.get("correlationId")).isNotBlank();
        if (exception == null) {
            assertThat(summary.get("exceptionClass")).isNull();
        } else {
            assertThat((String) summary.get("exceptionClass")).endsWith(exception);
            assertThat(list(detail.get("diagnoses"))).isNotEmpty();
            assertThat((String) detail.get("stackTrace")).contains(exception);
        }
        assertThat((Integer) detail.get("stepCount")).isGreaterThan(3);
        assertThat((Integer) detail.get("logCount")).isPositive();
        // dados sensíveis nunca persistidos em claro
        assertThat(String.valueOf(detail.get("requestHeaders"))).doesNotContain("runner-lab");
    }

    @Test
    void persistsRealTimelineWithInstrumentedLayers() {
        Map<String, Object> detail = execute("D01", "DEFAULT");
        Object id = summary(detail).get("id");
        Map<?, ?> timeline = client().get().uri("/api/v1/executions/{id}/timeline", id)
                .header(HttpHeaders.AUTHORIZATION, adminToken()).retrieve().body(Map.class);
        List<Map<String, Object>> steps = list(timeline.get("steps"));
        assertThat(steps).extracting(s -> s.get("layer"))
                .contains("RUNNER", "HTTP", "SECURITY", "CONTROLLER", "SERVICE", "REPOSITORY", "DATABASE",
                        "EXCEPTION_HANDLER", "RESPONSE");
        assertThat(steps).anyMatch(s -> "DATABASE".equals(s.get("layer")) && "ERROR".equals(s.get("status"))
                && String.valueOf(s.get("errorMessage")).contains("23505"));
        List<Map<String, Object>> stages = list(timeline.get("stages"));
        assertThat(stages.getFirst().get("instrumented")).isEqualTo(false);
        assertThat(stages).anyMatch(s -> "EXTERNAL".equals(s.get("key")) && Boolean.FALSE.equals(s.get("reached")));
    }

    @Test
    void verifiesTransactionalRollbackAutomatically() {
        Map<String, Object> failure = execute("D04", "FAILURE");
        Map<?, ?> verification = (Map<?, ?>) failure.get("verification");
        assertThat(verification.get("conclusion")).isEqualTo("ROLLBACK_CONFIRMADO");
        assertThat(verification.get("passed")).isEqualTo(true);
        assertThat(list(failure.get("diagnoses"))).extracting(d -> d.get("ruleId")).contains("TRANSACTION_ROLLBACK");

        Map<String, Object> success = execute("D04", "SUCCESS");
        assertThat(summary(success).get("httpStatus")).isEqualTo(201);
        assertThat(((Map<?, ?>) success.get("verification")).get("conclusion")).isEqualTo("COMMIT_CONFIRMADO");
    }

    @Test
    void compareModesHighlightsFirstDivergence() {
        Map<?, ?> result = client().post().uri("/api/v1/scenarios/A02/compare-modes")
                .header(HttpHeaders.AUTHORIZATION, adminToken()).retrieve().body(Map.class);
        Map<?, ?> comparison = (Map<?, ?>) result.get("comparison");
        assertThat(((Map<?, ?>) comparison.get("left")).get("httpStatus")).isEqualTo(201);
        assertThat(((Map<?, ?>) comparison.get("right")).get("httpStatus")).isEqualTo(409);
        Map<?, ?> divergence = (Map<?, ?>) comparison.get("firstDivergence");
        assertThat(divergence.get("layer")).isEqualTo("DATABASE");
        assertThat((String) comparison.get("causalityNote")).contains("não necessariamente a causa");
    }

    @Test
    void historyFiltersAndCorrelationLookupWork() {
        Map<String, Object> detail = execute("B01", "DEFAULT");
        String correlationId = (String) summary(detail).get("correlationId");
        String token = adminToken();

        Map<?, ?> page = client().get().uri("/api/v1/executions?correlationId={c}", correlationId)
                .header(HttpHeaders.AUTHORIZATION, token).retrieve().body(Map.class);
        assertThat(list(page.get("content"))).hasSize(1);

        Map<?, ?> byException = client().get().uri("/api/v1/executions?exception=ResourceNotFound&outcome=FAILURE")
                .header(HttpHeaders.AUTHORIZATION, token).retrieve().body(Map.class);
        assertThat(((Number) byException.get("totalElements")).longValue()).isPositive();

        Map<?, ?> found = client().get().uri("/api/v1/executions/by-correlation/{c}", correlationId)
                .header(HttpHeaders.AUTHORIZATION, token).retrieve().body(Map.class);
        assertThat(((Map<?, ?>) found.get("summary")).get("scenarioCode")).isEqualTo("B01");

        Map<?, ?> dashboard = client().get().uri("/api/v1/dashboard/summary?range=24h")
                .header(HttpHeaders.AUTHORIZATION, token).retrieve().body(Map.class);
        assertThat(((Number) dashboard.get("total")).longValue()).isPositive();
    }

    @Test
    void exportsReportsInAllFormats() {
        Object id = summary(execute("E03", "DEFAULT")).get("id");
        String token = adminToken();
        for (String format : List.of("json", "markdown", "pdf")) {
            var response = client().get().uri("/api/v1/executions/{id}/report?format={f}", id, format)
                    .header(HttpHeaders.AUTHORIZATION, token).retrieve().toEntity(byte[].class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotEmpty();
        }
        String markdown = client().get().uri("/api/v1/executions/{id}/report?format=markdown", id)
                .header(HttpHeaders.AUTHORIZATION, token).retrieve().body(String.class);
        assertThat(markdown).contains("Caused by").contains("NumberFormatException").contains("Diagnóstico");
    }
}
