package dev.errorlens.domain.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Registro persistido de uma execução de cenário, com dados sanitizados de requisição,
 * resposta, exception e instrumentação.
 */
@Entity
@Table(name = "executions")
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_code", nullable = false)
    private String scenarioCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionMode mode;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "span_id")
    private String spanId;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionOutcome outcome;

    @Column(name = "expected_status")
    private Integer expectedStatus;

    @Column(name = "matched_expectation", nullable = false)
    private boolean matchedExpectation;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "request_headers")
    private String requestHeaders;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "response_headers")
    private String responseHeaders;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "exception_class")
    private String exceptionClass;

    @Column(name = "exception_message")
    private String exceptionMessage;

    @Column(name = "root_cause_class")
    private String rootCauseClass;

    @Column(name = "root_cause_message")
    private String rootCauseMessage;

    @Column(name = "exception_chain")
    private String exceptionChain;

    @Column(name = "stack_trace")
    private String stackTrace;

    @Column
    private String verification;

    @Column(name = "comparison_group")
    private String comparisonGroup;

    @Column(name = "triggered_by")
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<ExecutionStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<ExecutionLog> logs = new ArrayList<>();

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    private List<DiagnosisRecord> diagnoses = new ArrayList<>();

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attemptNumber ASC")
    private List<ScenarioAttempt> attempts = new ArrayList<>();

    protected Execution() {
    }

    public Execution(String scenarioCode, ExecutionMode mode, String correlationId, String httpMethod,
                     String endpoint, Instant startedAt) {
        this.scenarioCode = scenarioCode;
        this.mode = mode;
        this.correlationId = correlationId;
        this.httpMethod = httpMethod;
        this.endpoint = endpoint;
        this.startedAt = startedAt;
        this.outcome = ExecutionOutcome.FAILURE;
    }

    public void addStep(ExecutionStep step) {
        step.attachTo(this);
        steps.add(step);
    }

    public void addLog(ExecutionLog log) {
        log.attachTo(this);
        logs.add(log);
    }

    public void addDiagnosis(DiagnosisRecord diagnosis) {
        diagnosis.attachTo(this);
        diagnoses.add(diagnosis);
    }

    public void addAttempt(ScenarioAttempt attempt) {
        attempt.attachTo(this);
        attempts.add(attempt);
    }

    public Long getId() {
        return id;
    }

    public String getScenarioCode() {
        return scenarioCode;
    }

    public ExecutionMode getMode() {
        return mode;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public ExecutionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ExecutionOutcome outcome) {
        this.outcome = outcome;
    }

    public Integer getExpectedStatus() {
        return expectedStatus;
    }

    public void setExpectedStatus(Integer expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public boolean isMatchedExpectation() {
        return matchedExpectation;
    }

    public void setMatchedExpectation(boolean matchedExpectation) {
        this.matchedExpectation = matchedExpectation;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getRootCauseClass() {
        return rootCauseClass;
    }

    public void setRootCauseClass(String rootCauseClass) {
        this.rootCauseClass = rootCauseClass;
    }

    public String getRootCauseMessage() {
        return rootCauseMessage;
    }

    public void setRootCauseMessage(String rootCauseMessage) {
        this.rootCauseMessage = rootCauseMessage;
    }

    public String getExceptionChain() {
        return exceptionChain;
    }

    public void setExceptionChain(String exceptionChain) {
        this.exceptionChain = exceptionChain;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getVerification() {
        return verification;
    }

    public void setVerification(String verification) {
        this.verification = verification;
    }

    public String getComparisonGroup() {
        return comparisonGroup;
    }

    public void setComparisonGroup(String comparisonGroup) {
        this.comparisonGroup = comparisonGroup;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExecutionStep> getSteps() {
        return steps;
    }

    public List<ExecutionLog> getLogs() {
        return logs;
    }

    public List<DiagnosisRecord> getDiagnoses() {
        return diagnoses;
    }

    public List<ScenarioAttempt> getAttempts() {
        return attempts;
    }
}
