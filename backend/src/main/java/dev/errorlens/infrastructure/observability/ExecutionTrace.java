package dev.errorlens.infrastructure.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;

/**
 * Coleta em memória todos os eventos reais de instrumentação de uma execução
 * (etapas, logs, tentativas e exception tratada). É thread-safe porque etapas podem
 * ser registradas por threads auxiliares (ex.: TimeLimiter).
 */
public final class ExecutionTrace {

    static final int MAX_STEPS = 400;
    static final int MAX_LOGS = 500;
    static final int MAX_ATTEMPTS = 50;

    private final String key;
    private final String correlationId;
    private final Instant startedAt = Instant.now();
    private final long startNanos = System.nanoTime();
    private final List<StepRecord> steps = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final List<AttemptRecord> attempts = new ArrayList<>();
    private final CountDownLatch innerCompleted = new CountDownLatch(1);
    private int stepSeq;
    private int logSeq;
    private int droppedEvents;
    private volatile Throwable handledException;
    private volatile String errorCode;
    private volatile String traceId;
    private volatile String spanId;
    private volatile boolean closed;

    ExecutionTrace(String key, String correlationId) {
        this.key = key;
        this.correlationId = correlationId;
    }

    public String key() {
        return key;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public synchronized StepRecord open(StepLayer layer, String name, String component, int depth, String detail) {
        StepRecord step = newStep(layer, name, component, depth, System.nanoTime());
        if (step == null) {
            return null;
        }
        step.open = true;
        step.status = StepStatus.OK;
        step.detail = detail;
        return step;
    }

    public synchronized StepRecord event(StepLayer layer, String name, String component, int depth,
                                         StepStatus status, String detail, Throwable error) {
        StepRecord step = newStep(layer, name, component, depth, System.nanoTime());
        if (step == null) {
            return null;
        }
        step.status = status;
        step.detail = detail;
        applyError(step, error);
        return step;
    }

    /**
     * Evento com duração medida externamente (ex.: tempo reportado pelo driver JDBC).
     */
    public synchronized StepRecord timedEvent(StepLayer layer, String name, String component, int depth,
                                              StepStatus status, long durationMicros, String detail,
                                              String errorClass, String errorMessage) {
        long startNs = System.nanoTime() - durationMicros * 1_000L;
        StepRecord step = newStep(layer, name, component, depth, startNs);
        if (step == null) {
            return null;
        }
        step.status = status;
        step.durationMicros = durationMicros;
        step.detail = detail;
        step.errorClass = errorClass;
        step.errorMessage = errorMessage;
        return step;
    }

    public synchronized void close(StepRecord step, StepStatus status, String detail, Throwable error) {
        if (step == null || !step.open) {
            return;
        }
        step.open = false;
        step.durationMicros = Math.max(0, (System.nanoTime() - step.startNanos) / 1_000L);
        step.status = status;
        if (detail != null) {
            step.detail = detail;
        }
        applyError(step, error);
    }

    public synchronized void addLog(Instant timestamp, String level, String logger, String thread, String message,
                                    String exceptionClass) {
        if (closed || logs.size() >= MAX_LOGS) {
            droppedEvents++;
            return;
        }
        logs.add(new LogRecord(++logSeq, timestamp, level, logger, thread, message, exceptionClass));
    }

    public synchronized int addAttempt(String target, String operation, Instant startedAt, Long durationMs,
                                       String outcome, Integer httpStatus, String errorClass, String circuitState,
                                       String detail) {
        if (closed || attempts.size() >= MAX_ATTEMPTS) {
            droppedEvents++;
            return -1;
        }
        int number = attempts.size() + 1;
        attempts.add(new AttemptRecord(number, target, operation, startedAt, durationMs, outcome, httpStatus,
                errorClass, circuitState, detail));
        return number;
    }

    public void recordHandledException(Throwable exception, String errorCode) {
        this.handledException = exception;
        this.errorCode = errorCode;
    }

    public void recordTraceContext(String traceId, String spanId) {
        if (this.traceId == null && traceId != null) {
            this.traceId = traceId;
            this.spanId = spanId;
        }
    }

    public void markInnerCompleted() {
        innerCompleted.countDown();
    }

    public boolean awaitInnerCompletion(long timeoutMillis) {
        try {
            return innerCompleted.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Fecha o trace: etapas ainda abertas recebem status WARN (sem duração) e novos eventos são ignorados.
     */
    public synchronized void seal() {
        for (StepRecord step : steps) {
            if (step.open) {
                step.open = false;
                step.status = StepStatus.WARN;
                step.detail = (step.detail == null ? "" : step.detail + " ")
                        + "(etapa não finalizada antes do encerramento da execução; duração não medida)";
            }
        }
        closed = true;
    }

    public synchronized List<StepRecord> steps() {
        return List.copyOf(steps);
    }

    public synchronized List<LogRecord> logs() {
        return List.copyOf(logs);
    }

    public synchronized List<AttemptRecord> attempts() {
        return List.copyOf(attempts);
    }

    public synchronized int droppedEvents() {
        return droppedEvents;
    }

    public Throwable handledException() {
        return handledException;
    }

    public String errorCode() {
        return errorCode;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    private StepRecord newStep(StepLayer layer, String name, String component, int depth, long startNs) {
        if (closed || steps.size() >= MAX_STEPS) {
            droppedEvents++;
            return null;
        }
        long offsetMicros = Math.max(0, (startNs - startNanos) / 1_000L);
        Instant at = startedAt.plusNanos(offsetMicros * 1_000L);
        StepRecord step = new StepRecord(++stepSeq, layer, name, component, Math.max(0, depth), at, startNs,
                offsetMicros);
        steps.add(step);
        return step;
    }

    private static void applyError(StepRecord step, Throwable error) {
        if (error != null) {
            step.errorClass = error.getClass().getName();
            step.errorMessage = SensitiveDataSanitizer.text(error.getMessage(), 1000);
        }
    }
}
