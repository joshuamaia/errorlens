package dev.errorlens.application.scenario;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;

import dev.errorlens.domain.execution.ExecutionMode;

/**
 * Comportamento executável de um cenário do catálogo. {@link #plan} pode preparar dados
 * (ex.: criar um cliente descartável) e devolve a requisição HTTP real a ser enviada.
 */
public interface ScenarioHandler {

    String code();

    Set<ExecutionMode> supportedModes();

    ScenarioPlan plan(ExecutionMode mode, ScenarioSupport support);

    /**
     * Nome de um recurso compartilhado (ex.: um circuit breaker) que exige execução exclusiva.
     */
    default String exclusiveResource() {
        return null;
    }

    static ScenarioHandler of(String code, BiFunction<ExecutionMode, ScenarioSupport, ScenarioPlan> planner) {
        return of(code, null, true, planner);
    }

    static ScenarioHandler single(String code, BiFunction<ExecutionMode, ScenarioSupport, ScenarioPlan> planner) {
        return of(code, null, false, planner);
    }

    static ScenarioHandler of(String code, String exclusiveResource, boolean compareModes,
                              BiFunction<ExecutionMode, ScenarioSupport, ScenarioPlan> planner) {
        Set<ExecutionMode> modes = compareModes ? EnumSet.allOf(ExecutionMode.class)
                : EnumSet.of(ExecutionMode.DEFAULT);
        return new ScenarioHandler() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public Set<ExecutionMode> supportedModes() {
                return modes;
            }

            @Override
            public ScenarioPlan plan(ExecutionMode mode, ScenarioSupport support) {
                return planner.apply(mode, support);
            }

            @Override
            public String exclusiveResource() {
                return exclusiveResource;
            }
        };
    }
}
