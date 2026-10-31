package dev.errorlens.infrastructure.observability;

import java.time.Instant;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;

/**
 * Etapa mutável registrada em memória durante a execução; convertida em
 * {@code ExecutionStep} ao final.
 */
public final class StepRecord {

    final int seq;
    final StepLayer layer;
    final String name;
    final String component;
    final int depth;
    final Instant startedAt;
    final long startNanos;
    final long offsetMicros;
    Long durationMicros;
    StepStatus status;
    String detail;
    String errorClass;
    String errorMessage;
    boolean open;

    StepRecord(int seq, StepLayer layer, String name, String component, int depth, Instant startedAt,
               long startNanos, long offsetMicros) {
        this.seq = seq;
        this.layer = layer;
        this.name = name;
        this.component = component;
        this.depth = depth;
        this.startedAt = startedAt;
        this.startNanos = startNanos;
        this.offsetMicros = offsetMicros;
    }

    public int seq() {
        return seq;
    }

    public StepLayer layer() {
        return layer;
    }

    public String name() {
        return name;
    }

    public String component() {
        return component;
    }

    public int depth() {
        return depth;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long offsetMicros() {
        return offsetMicros;
    }

    public Long durationMicros() {
        return durationMicros;
    }

    public StepStatus status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public String errorClass() {
        return errorClass;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public boolean isOpen() {
        return open;
    }
}
