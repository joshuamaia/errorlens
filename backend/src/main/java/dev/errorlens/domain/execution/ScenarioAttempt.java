package dev.errorlens.domain.execution;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tentativa de chamada a uma dependência externa (retries, circuit breaker, timeout).
 */
@Entity
@Table(name = "scenario_attempts")
public class ScenarioAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private String operation;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_class")
    private String errorClass;

    @Column(name = "circuit_state")
    private String circuitState;

    @Column
    private String detail;

    protected ScenarioAttempt() {
    }

    public ScenarioAttempt(int attemptNumber, String target, String operation, Instant startedAt, Long durationMs,
                           String outcome, Integer httpStatus, String errorClass, String circuitState, String detail) {
        this.attemptNumber = attemptNumber;
        this.target = target;
        this.operation = operation;
        this.startedAt = startedAt;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
        this.errorClass = errorClass;
        this.circuitState = circuitState;
        this.detail = detail;
    }

    void attachTo(Execution execution) {
        this.execution = execution;
    }

    public Long getId() {
        return id;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public String getTarget() {
        return target;
    }

    public String getOperation() {
        return operation;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getOutcome() {
        return outcome;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public String getCircuitState() {
        return circuitState;
    }

    public String getDetail() {
        return detail;
    }
}
