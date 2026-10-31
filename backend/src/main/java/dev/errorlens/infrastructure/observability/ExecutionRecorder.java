package dev.errorlens.infrastructure.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registro de execuções em andamento. Somente requisições cuja chave foi registrada aqui
 * pelo Scenario Runner são instrumentadas, impedindo que clientes externos injetem traces.
 */
@Component
public class ExecutionRecorder {

    private static final Duration MAX_AGE = Duration.ofMinutes(3);
    private static volatile ExecutionRecorder instance;

    private final Map<String, ExecutionTrace> active = new ConcurrentHashMap<>();

    public ExecutionRecorder() {
        instance = this;
    }

    /**
     * Acesso estático usado pelo appender do Logback, que não é gerenciado pelo Spring.
     */
    public static ExecutionTrace lookupStatic(String key) {
        ExecutionRecorder recorder = instance;
        return recorder == null || key == null ? null : recorder.active.get(key);
    }

    public ExecutionTrace start(String correlationId) {
        String key = UUID.randomUUID().toString();
        ExecutionTrace trace = new ExecutionTrace(key, correlationId);
        active.put(key, trace);
        return trace;
    }

    public ExecutionTrace find(String key) {
        return key == null ? null : active.get(key);
    }

    public void finish(ExecutionTrace trace) {
        trace.seal();
        active.remove(trace.key());
    }

    @Scheduled(fixedDelay = 60_000)
    void evictStale() {
        Instant limit = Instant.now().minus(MAX_AGE);
        active.values().removeIf(trace -> {
            boolean stale = trace.startedAt().isBefore(limit);
            if (stale) {
                trace.seal();
            }
            return stale;
        });
    }
}
