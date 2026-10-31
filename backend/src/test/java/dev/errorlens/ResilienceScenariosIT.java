package dev.errorlens;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.errorlens.support.IntegrationTestBase;

/**
 * Cenários de resiliência com o simulador em processo: timeout, retry, circuit breaker e fallback.
 */
class ResilienceScenariosIT extends IntegrationTestBase {

    @Test
    void timeLimiterCancelsSlowDependency() {
        Map<String, Object> detail = execute("F01", "DEFAULT");
        assertThat(summary(detail).get("httpStatus")).isEqualTo(504);
        assertThat((String) summary(detail).get("rootCauseClass")).isEqualTo("java.util.concurrent.TimeoutException");
        assertThat(list(detail.get("attempts")).getFirst().get("outcome")).isEqualTo("TIMEOUT");
        assertThat(((Number) summary(detail).get("durationMs")).longValue()).isLessThan(3500);
    }

    @Test
    void retryRecoversAfterTransientFailures() {
        Map<String, Object> detail = execute("F02", "DEFAULT");
        assertThat(summary(detail).get("httpStatus")).isEqualTo(200);
        List<Map<String, Object>> attempts = list(detail.get("attempts"));
        assertThat(attempts).extracting(a -> a.get("outcome")).containsExactly("FAILURE", "FAILURE", "SUCCESS");
        assertThat(list(detail.get("diagnoses"))).extracting(d -> d.get("ruleId")).contains("RETRY_RECOVERED");
    }

    @Test
    void retryExhaustionBecomesBadGateway() {
        Map<String, Object> detail = execute("F03", "DEFAULT");
        assertThat(summary(detail).get("httpStatus")).isEqualTo(502);
        assertThat(list(detail.get("attempts"))).hasSize(4).allMatch(a -> "FAILURE".equals(a.get("outcome")));
    }

    @Test
    void circuitBreakerWalksThroughAllStates() {
        Map<String, Object> failure = execute("F04", "FAILURE");
        assertThat(summary(failure).get("httpStatus")).isEqualTo(503);
        Object id = summary(failure).get("id");
        Map<?, ?> timeline = client().get().uri("/api/v1/executions/{id}/timeline", id)
                .header("Authorization", adminToken()).retrieve().body(Map.class);
        List<Object> names = list(timeline.get("steps")).stream().map(s -> s.get("name")).toList();
        assertThat(names).contains("Circuit Breaker · OPEN", "Circuit Breaker · HALF_OPEN",
                "Circuit Breaker · chamada rejeitada");
        assertThat(list(failure.get("attempts"))).anyMatch(a -> "REJECTED".equals(a.get("outcome")));

        Map<String, Object> recovered = execute("F04", "SUCCESS");
        assertThat(summary(recovered).get("httpStatus")).isEqualTo(200);
        assertThat((String) recovered.get("responseBody")).contains("\"finalState\" : \"CLOSED\"");
    }

    @Test
    void fallbackIsClassifiedAsDegraded() {
        Map<String, Object> degraded = execute("F05", "FAILURE");
        assertThat(summary(degraded).get("httpStatus")).isEqualTo(200);
        assertThat(summary(degraded).get("outcome")).isEqualTo("DEGRADED");
        assertThat(summary(degraded).get("matchedExpectation")).isEqualTo(true);

        Map<String, Object> normal = execute("F05", "SUCCESS");
        assertThat(summary(normal).get("outcome")).isEqualTo("SUCCESS");
        assertThat(SIMULATOR.behaviorCount()).isZero();
    }
}
