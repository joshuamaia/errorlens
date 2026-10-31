package dev.errorlens.application.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Representações de leitura das execuções expostas pela API.
 */
public final class ExecutionViews {

    private ExecutionViews() {
    }

    public record Summary(Long id, String scenarioCode, String scenarioName, String mode, String correlationId,
                          String traceId, String httpMethod, String endpoint, Integer httpStatus, String outcome,
                          Integer expectedStatus, boolean matchedExpectation, Instant startedAt, Long durationMs,
                          String errorCode, String exceptionClass, String rootCauseClass, String comparisonGroup) {
    }

    public record Detail(Summary summary, String scenarioCategory, String scenarioDescription,
                         Map<String, Object> requestHeaders, String requestBody,
                         Map<String, Object> responseHeaders, String responseBody,
                         String exceptionMessage, String rootCauseMessage, List<Map<String, Object>> exceptionChain,
                         String stackTrace, Map<String, Object> verification, List<DiagnosisView> diagnoses,
                         List<AttemptView> attempts, List<String> components, int stepCount, int logCount,
                         String triggeredBy, Instant finishedAt, String jaegerTraceUrl, String diagnosisDisclaimer) {
    }

    public record StepView(int seq, String layer, String layerLabel, String name, String component, int depth,
                           String status, Instant startedAt, long offsetMicros, Long durationMicros, String detail,
                           String errorClass, String errorMessage) {
    }

    public record LogView(int seq, Instant timestamp, String level, String logger, String thread, String message,
                          String exceptionClass) {
    }

    public record AttemptView(int number, String target, String operation, Instant startedAt, Long durationMs,
                              String outcome, Integer httpStatus, String errorClass, String circuitState,
                              String detail) {
    }

    public record DiagnosisView(int rank, String ruleId, String title, String category, String description,
                                String confidence, int score, List<String> possibleCauses, List<String> evidences,
                                List<String> suggestions, List<String> fixes, String codeExample) {
    }

    public record Stage(String key, String label, boolean instrumented, boolean reached, String status,
                        Long durationMicros, int stepCount, String note) {
    }

    public record Timeline(Long executionId, Instant startedAt, Long durationMs, List<Stage> stages,
                           List<StepView> steps, String note) {
    }
}
