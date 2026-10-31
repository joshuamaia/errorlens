package dev.errorlens.simulator;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Comportamentos por chave de simulação, com contador de chamadas para FAIL_FIRST_N.
 * O número de chaves e o tempo de vida são limitados para evitar crescimento indefinido.
 */
@Component
public class BehaviorRegistry {

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final SimulatorProperties properties;

    public BehaviorRegistry(SimulatorProperties properties) {
        this.properties = properties;
    }

    public boolean configure(String key, Behavior behavior) {
        if (!states.containsKey(key) && states.size() >= properties.maxBehaviors()) {
            evictExpired();
            if (states.size() >= properties.maxBehaviors()) {
                return false;
            }
        }
        states.put(key, new State(behavior, new AtomicInteger(), Instant.now()));
        return true;
    }

    public Optional<State> find(String key) {
        return Optional.ofNullable(key == null ? null : states.get(key));
    }

    /**
     * Registra uma chamada e decide a resposta de acordo com o comportamento configurado.
     */
    public Decision decide(String key) {
        State state = key == null ? null : states.get(key);
        Behavior behavior = state == null ? Behavior.immediate() : state.behavior();
        int call = state == null ? 1 : state.calls().incrementAndGet();
        int delay = Math.min(behavior.delayMs(), properties.maxDelayMs());
        return switch (behavior.mode()) {
            case IMMEDIATE -> new Decision(200, 0, call, behavior.mode());
            case SLOW -> new Decision(200, delay, call, behavior.mode());
            case ERROR_500 -> new Decision(500, delay, call, behavior.mode());
            case ERROR_503 -> new Decision(503, delay, call, behavior.mode());
            case FAIL_FIRST_N -> new Decision(call <= behavior.failures() ? statusOr(behavior.status(), 503) : 200,
                    delay, call, behavior.mode());
            case UNAVAILABLE -> new Decision(503, 0, call, behavior.mode());
        };
    }

    public boolean remove(String key) {
        return states.remove(key) != null;
    }

    public int reset() {
        int size = states.size();
        states.clear();
        return size;
    }

    public int size() {
        return states.size();
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        Instant limit = Instant.now().minus(properties.behaviorTtl());
        states.values().removeIf(state -> state.createdAt().isBefore(limit));
    }

    private static int statusOr(int status, int fallback) {
        return status >= 500 ? status : fallback;
    }

    public record State(Behavior behavior, AtomicInteger calls, Instant createdAt) {
    }

    public record Decision(int status, int delayMs, int call, Behavior.Mode mode) {
    }
}
