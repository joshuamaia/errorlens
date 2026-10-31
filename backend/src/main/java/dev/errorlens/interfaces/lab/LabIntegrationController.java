package dev.errorlens.interfaces.lab;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.lab.LabIntegrationService;
import dev.errorlens.infrastructure.observability.MdcKeys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Integrações com o dependency-simulator (cenários F01-F05). O header X-Simulation-Key
 * seleciona o comportamento previamente configurado no simulador pelo Scenario Runner.
 */
@RestController
@RequestMapping("/api/v1/lab/integrations")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Laboratório · Integrações", description = "Timeout, retry, circuit breaker e fallback")
public class LabIntegrationController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final LabIntegrationService integrationService;

    public LabIntegrationController(LabIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/credit-score/{customerId}")
    @Operation(summary = "Score de crédito com TimeLimiter")
    public Map<String, Object> creditScore(@PathVariable long customerId,
                                           @RequestHeader(name = MdcKeys.SIMULATION_KEY_HEADER, required = false)
                                           String key) {
        return integrationService.creditScore(customerId, validKey(key));
    }

    @GetMapping("/customer-profile/{customerId}")
    @Operation(summary = "Perfil externo com Retry")
    public Map<String, Object> customerProfile(@PathVariable long customerId,
                                               @RequestHeader(name = MdcKeys.SIMULATION_KEY_HEADER, required = false)
                                               String key) {
        return integrationService.customerProfile(customerId, validKey(key));
    }

    @PostMapping("/payments/circuit-demo")
    @Operation(summary = "Demonstração dos estados do Circuit Breaker")
    public Map<String, Object> circuitDemo(@RequestHeader(name = MdcKeys.SIMULATION_KEY_HEADER, required = false)
                                           String key) {
        return integrationService.circuitBreakerDemo(validKey(key));
    }

    @GetMapping("/recommendations/{customerId}")
    @Operation(summary = "Recomendações com fallback")
    public ResponseEntity<LabIntegrationService.RecommendationResult> recommendations(
            @PathVariable long customerId,
            @RequestHeader(name = MdcKeys.SIMULATION_KEY_HEADER, required = false) String key) {
        LabIntegrationService.RecommendationResult result = integrationService.recommendations(customerId,
                validKey(key));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.degraded()) {
            response.header(MdcKeys.DEGRADED_HEADER, "true");
        }
        return response.body(result);
    }

    private static String validKey(String key) {
        if (key == null) {
            return null;
        }
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("X-Simulation-Key inválida.");
        }
        return key;
    }
}
