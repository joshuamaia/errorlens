package dev.errorlens.application.execution;

import java.time.Instant;
import java.util.Map;

import dev.errorlens.application.scenario.LabRequestDispatcher.DispatchResult;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.application.scenario.Verification;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.observability.ExecutionTrace;

/**
 * Tudo o que foi observado durante uma execução, pronto para ser persistido.
 *
 * @param responseTimeMs tempo de ida e volta da requisição HTTP (null se não houve resposta)
 * @param runnerError    falha do próprio runner (preparação ou comunicação), quando houver
 */
public record ExecutionCapture(
        Scenario scenario,
        ExecutionMode mode,
        ScenarioPlan plan,
        ExecutionTrace trace,
        Map<String, String> sentHeaders,
        DispatchResult result,
        Verification verification,
        Throwable runnerError,
        Instant startedAt,
        long totalMs,
        Long responseTimeMs,
        String triggeredBy,
        String comparisonGroup) {
}
