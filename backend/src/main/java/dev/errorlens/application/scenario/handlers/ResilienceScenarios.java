package dev.errorlens.application.scenario.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.errorlens.application.scenario.ScenarioHandler;
import dev.errorlens.application.scenario.ScenarioHandlerGroup;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.application.scenario.ScenarioSupport;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.infrastructure.resilience.ResilienceEventRecorder;
import dev.errorlens.infrastructure.security.LabIdentities;
import dev.errorlens.infrastructure.simulator.SimulatorBehavior;

/**
 * Cenários de resiliência (F01-F05). O comportamento do dependency-simulator é configurado
 * por execução (chave de simulação isolada) e os circuitos são reiniciados na preparação.
 */
@Component
public class ResilienceScenarios implements ScenarioHandlerGroup {

    private static final String BASE = "/api/v1/lab/integrations";

    @Override
    public List<ScenarioHandler> handlers() {
        return List.of(
                ScenarioHandler.of("F01", (mode, s) -> {
                    long id = s.seedCustomerId(ScenarioSupport.SEED_ANA);
                    boolean slow = mode != ExecutionMode.SUCCESS;
                    return ScenarioPlan.request("GET", BASE + "/credit-score/" + id).as(LabIdentities.RUNNER)
                            .simulator(slow ? SimulatorBehavior.slow(3500) : SimulatorBehavior.immediate())
                            .expect(slow ? 504 : 200)
                            .variant(slow ? "Simulador responde em 3500 ms; TimeLimiter de 1500 ms"
                                    : "Simulador responde imediatamente").build();
                }),
                ScenarioHandler.single("F02", (mode, s) -> {
                    long id = s.seedCustomerId(ScenarioSupport.SEED_BRUNO);
                    return ScenarioPlan.request("GET", BASE + "/customer-profile/" + id).as(LabIdentities.RUNNER)
                            .simulator(SimulatorBehavior.failFirst(2)).expect(200)
                            .variant("Simulador falha (503) nas 2 primeiras chamadas e responde na 3ª").build();
                }),
                ScenarioHandler.of("F03", (mode, s) -> {
                    long id = s.seedCustomerId(ScenarioSupport.SEED_BRUNO);
                    boolean down = mode != ExecutionMode.SUCCESS;
                    return ScenarioPlan.request("GET", BASE + "/customer-profile/" + id).as(LabIdentities.RUNNER)
                            .simulator(down ? SimulatorBehavior.error503() : SimulatorBehavior.immediate())
                            .expect(down ? 502 : 200)
                            .variant(down ? "Simulador responde 503 em todas as chamadas"
                                    : "Simulador saudável").build();
                }),
                ScenarioHandler.of("F04", ResilienceEventRecorder.PAYMENT_CIRCUIT, true, (mode, s) -> {
                    s.resetCircuit(ResilienceEventRecorder.PAYMENT_CIRCUIT);
                    boolean recovers = mode == ExecutionMode.SUCCESS;
                    return ScenarioPlan.request("POST", BASE + "/payments/circuit-demo").as(LabIdentities.RUNNER)
                            .simulator(SimulatorBehavior.failFirst(recovers ? 4 : 6))
                            .expect(recovers ? 200 : 503)
                            .variant(recovers
                                    ? "4 falhas abrem o circuito; chamadas de teste em HALF_OPEN são bem-sucedidas"
                                    : "6 falhas: chamadas de teste em HALF_OPEN também falham e o circuito reabre")
                            .build();
                }),
                ScenarioHandler.of("F05", ResilienceEventRecorder.RECOMMENDATIONS_CIRCUIT, true, (mode, s) -> {
                    s.resetCircuit(ResilienceEventRecorder.RECOMMENDATIONS_CIRCUIT);
                    long id = s.seedCustomerId(ScenarioSupport.SEED_ANA);
                    boolean healthy = mode == ExecutionMode.SUCCESS;
                    ScenarioPlan.Builder plan = ScenarioPlan.request("GET", BASE + "/recommendations/" + id)
                            .as(LabIdentities.RUNNER)
                            .simulator(healthy ? SimulatorBehavior.immediate() : SimulatorBehavior.error500())
                            .expect(200)
                            .variant(healthy ? "Simulador saudável: recomendações personalizadas"
                                    : "Simulador responde 500: fallback com recomendações padrão");
                    if (!healthy) {
                        plan.expectDegraded();
                    }
                    return plan.build();
                }));
    }
}
