package dev.errorlens.interfaces.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.execution.RetentionService;
import dev.errorlens.infrastructure.simulator.SimulatorControlClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reset do laboratório (somente perfil lab, ROLE_ADMIN): circuitos, simulador e dados descartáveis.
 */
@RestController
@RequestMapping("/api/v1/lab-admin")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Administração do laboratório", description = "Mecanismos de reset para execuções reproduzíveis")
public class LabAdminController {

    private static final Logger log = LoggerFactory.getLogger(LabAdminController.class);

    private final CircuitBreakerRegistry circuitBreakers;
    private final SimulatorControlClient simulator;
    private final RetentionService retention;

    public LabAdminController(CircuitBreakerRegistry circuitBreakers, SimulatorControlClient simulator,
                              RetentionService retention) {
        this.circuitBreakers = circuitBreakers;
        this.simulator = simulator;
        this.retention = retention;
    }

    @PostMapping("/reset")
    @Operation(summary = "Reinicia circuitos, comportamentos do simulador e remove dados descartáveis antigos")
    public Map<String, Object> reset() {
        Map<String, Object> result = new LinkedHashMap<>();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        result.put("circuitBreakers", circuitBreakers.getAllCircuitBreakers().stream()
                .map(cb -> cb.getName() + "=" + cb.getState()).toList());
        try {
            simulator.resetAll();
            result.put("simulator", "RESET");
        } catch (RuntimeException ex) {
            result.put("simulator", "INDISPONÍVEL (" + ex.getClass().getSimpleName() + ")");
        }
        result.put("disposableData", retention.cleanDisposableData());
        log.info("Reset do laboratório executado: {}", result);
        return result;
    }
}
