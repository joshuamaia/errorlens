package dev.errorlens.application.scenario;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.errorlens.domain.execution.ExecutionOutcome;
import dev.errorlens.infrastructure.simulator.SimulatorBehavior;

/**
 * Requisição HTTP real que o Scenario Runner enviará ao backend, já com dados preparados.
 *
 * @param identity     identidade de laboratório usada em HTTP Basic, ou {@code null} para requisição anônima
 * @param rawBody      corpo exatamente como será enviado (permite JSON malformado)
 * @param simulator    comportamento do dependency-simulator para esta execução (opcional)
 * @param verification verificação executada após a resposta (ex.: confirmação de rollback)
 */
public record ScenarioPlan(
        String method,
        String path,
        String rawBody,
        String identity,
        Map<String, String> headers,
        int expectedStatus,
        ExecutionOutcome expectedOutcome,
        SimulatorBehavior simulator,
        Supplier<Verification> verification,
        String variant) {

    public static Builder request(String method, String path) {
        return new Builder(method, path);
    }

    public static final class Builder {
        private final String method;
        private final String path;
        private String rawBody;
        private String identity;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private int expectedStatus = 200;
        private ExecutionOutcome expectedOutcome;
        private SimulatorBehavior simulator;
        private Supplier<Verification> verification;
        private String variant;

        private Builder(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public Builder body(String rawBody) {
            this.rawBody = rawBody;
            return this;
        }

        public Builder as(String identity) {
            this.identity = identity;
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder expect(int status) {
            this.expectedStatus = status;
            return this;
        }

        public Builder expectDegraded() {
            this.expectedOutcome = ExecutionOutcome.DEGRADED;
            return this;
        }

        public Builder simulator(SimulatorBehavior behavior) {
            this.simulator = behavior;
            return this;
        }

        public Builder verify(Supplier<Verification> verification) {
            this.verification = verification;
            return this;
        }

        public Builder variant(String variant) {
            this.variant = variant;
            return this;
        }

        public ScenarioPlan build() {
            ExecutionOutcome outcome = expectedOutcome != null ? expectedOutcome
                    : expectedStatus >= 200 && expectedStatus < 300 ? ExecutionOutcome.SUCCESS
                    : ExecutionOutcome.FAILURE;
            return new ScenarioPlan(method, path, rawBody, identity, Map.copyOf(headers), expectedStatus, outcome,
                    simulator, verification, variant);
        }
    }
}
