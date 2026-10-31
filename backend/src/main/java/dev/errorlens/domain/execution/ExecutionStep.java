package dev.errorlens.domain.execution;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Evento de instrumentação real registrado durante a execução.
 * {@code durationMicros} é nulo quando o evento é pontual (sem duração medida).
 */
@Entity
@Table(name = "execution_steps")
public class ExecutionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepLayer layer;

    @Column(nullable = false)
    private String name;

    @Column
    private String component;

    @Column(nullable = false)
    private int depth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "offset_micros", nullable = false)
    private long offsetMicros;

    @Column(name = "duration_micros")
    private Long durationMicros;

    @Column
    private String detail;

    @Column(name = "error_class")
    private String errorClass;

    @Column(name = "error_message")
    private String errorMessage;

    protected ExecutionStep() {
    }

    public ExecutionStep(int seq, StepLayer layer, String name, String component, int depth, StepStatus status,
                         Instant startedAt, long offsetMicros, Long durationMicros, String detail,
                         String errorClass, String errorMessage) {
        this.seq = seq;
        this.layer = layer;
        this.name = name;
        this.component = component;
        this.depth = depth;
        this.status = status;
        this.startedAt = startedAt;
        this.offsetMicros = offsetMicros;
        this.durationMicros = durationMicros;
        this.detail = detail;
        this.errorClass = errorClass;
        this.errorMessage = errorMessage;
    }

    void attachTo(Execution execution) {
        this.execution = execution;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public StepLayer getLayer() {
        return layer;
    }

    public String getName() {
        return name;
    }

    public String getComponent() {
        return component;
    }

    public int getDepth() {
        return depth;
    }

    public StepStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getOffsetMicros() {
        return offsetMicros;
    }

    public Long getDurationMicros() {
        return durationMicros;
    }

    public String getDetail() {
        return detail;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
