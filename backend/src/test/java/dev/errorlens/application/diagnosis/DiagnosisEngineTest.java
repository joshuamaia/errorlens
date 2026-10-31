package dev.errorlens.application.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.errorlens.application.diagnosis.DiagnosisContext.AttemptInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.StepInfo;
import dev.errorlens.application.diagnosis.rules.DeclarativeRuleLoader;
import dev.errorlens.application.diagnosis.rules.ResilienceRules;
import tools.jackson.databind.json.JsonMapper;

class DiagnosisEngineTest {

    private static DiagnosisEngine engine;

    @BeforeAll
    static void setUp() throws Exception {
        ResilienceRules rules = new ResilienceRules();
        List<DiagnosisRule> programmatic = List.of(rules.retryExhaustedRule(), rules.retryRecoveredRule(),
                rules.circuitOpenRule(), rules.fallbackRule(), rules.transactionRollbackRule(),
                rules.slowExecutionRule());
        engine = new DiagnosisEngine(programmatic, new DeclarativeRuleLoader(JsonMapper.builder().build()));
    }

    @Test
    void diagnosesUniqueViolationWithHighConfidence() {
        DiagnosisContext context = new DiagnosisContext(DiagnosisContext.Source.EXECUTION, 409,
                "CUSTOMER_EMAIL_DUPLICATED", List.of(
                new ExceptionInfo("org.springframework.dao.DataIntegrityViolationException", "could not execute"),
                new ExceptionInfo("org.postgresql.util.PSQLException",
                        "ERROR: duplicate key value violates unique constraint \"uk_customers_email\"")),
                List.of(new StepInfo("DATABASE", "PostgreSQL · INSERT customers", "ERROR", null,
                        "SQLState 23505: duplicate key", null)), null, null, "FAILURE", 120L, null);

        List<DiagnosisResult> results = engine.diagnose(context);

        DiagnosisResult first = results.getFirst();
        assertThat(first.ruleId()).isEqualTo("DB_UNIQUE_VIOLATION");
        assertThat(first.confidence()).isEqualTo(DiagnosisResult.Confidence.ALTA);
        assertThat(first.score()).isLessThanOrEqualTo(95);
        assertThat(first.evidences()).anyMatch(e -> e.contains("SQLState 23505"));
        assertThat(results).noneMatch(r -> r.ruleId().equals("DB_FOREIGN_KEY_VIOLATION"));
    }

    @Test
    void diagnosesNullPointerFromStackTraceOnly() {
        List<DiagnosisResult> results = engine.diagnose(DiagnosisContext.ofExceptions(
                DiagnosisContext.Source.STACKTRACE, null, List.of(new ExceptionInfo("java.lang.NullPointerException",
                        "Cannot invoke \"X.y()\" because \"profile\" is null"))));
        assertThat(results.getFirst().ruleId()).isEqualTo("NULL_REFERENCE");
        assertThat(results.getFirst().possibleCauses()).isNotEmpty();
        assertThat(results.getFirst().codeExample()).isNotBlank();
    }

    @Test
    void detectsCircuitBreakerAndRetryPatterns() {
        DiagnosisContext circuit = new DiagnosisContext(DiagnosisContext.Source.EXECUTION, 503, "CIRCUIT_OPEN",
                List.of(new ExceptionInfo("io.github.resilience4j.circuitbreaker.CallNotPermittedException",
                        "CircuitBreaker 'paymentGateway' is OPEN")),
                List.of(new StepInfo("RESILIENCE", "Circuit Breaker · OPEN", "ERROR", null, null, "CLOSED -> OPEN")),
                null, List.of(new AttemptInfo(1, "REJECTED", null, null, "OPEN")), "FAILURE", 1400L, null);
        assertThat(engine.diagnose(circuit)).extracting(DiagnosisResult::ruleId).contains("CIRCUIT_BREAKER_OPEN");

        DiagnosisContext retry = new DiagnosisContext(DiagnosisContext.Source.EXECUTION, 502, "DEPENDENCY_FAILURE",
                List.of(new ExceptionInfo("dev.errorlens.domain.exception.ExternalDependencyException", "x")),
                List.of(new StepInfo("RESILIENCE", "Retry · esgotado", "ERROR", null, null, null)), null,
                List.of(new AttemptInfo(1, "FAILURE", 503, null, null), new AttemptInfo(2, "FAILURE", 503, null, null)),
                "FAILURE", 900L, null);
        assertThat(engine.diagnose(retry).getFirst().ruleId()).isEqualTo("RETRY_EXHAUSTED");
    }

    @Test
    void returnsNothingForCleanSuccess() {
        DiagnosisContext success = new DiagnosisContext(DiagnosisContext.Source.EXECUTION, 200, null, List.of(),
                List.of(), List.of(), List.of(), "SUCCESS", 80L, null);
        assertThat(engine.diagnose(success)).isEmpty();
    }

    @Test
    void flagsDegradedResponses() {
        DiagnosisContext degraded = new DiagnosisContext(DiagnosisContext.Source.EXECUTION, 200, null, List.of(),
                List.of(), List.of(), List.of(), "DEGRADED", 90L, java.util.Map.of("X-ErrorLens-Degraded", "true"));
        assertThat(engine.diagnose(degraded)).extracting(DiagnosisResult::ruleId).containsExactly("FALLBACK_DEGRADED");
    }
}
