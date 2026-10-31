package dev.errorlens.simulator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APIs de negócio simuladas. O comportamento de cada chamada depende da chave enviada no
 * header X-Simulation-Key (sem chave: resposta imediata e bem-sucedida).
 */
@RestController
@RequestMapping("/api/v1")
public class DependencyController {

    static final String KEY_HEADER = "X-Simulation-Key";
    private static final Logger log = LoggerFactory.getLogger(DependencyController.class);

    private final BehaviorRegistry registry;

    public DependencyController(BehaviorRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/credit-score/{customerId}")
    public ResponseEntity<Map<String, Object>> creditScore(@PathVariable long customerId,
                                                           @RequestHeader(name = KEY_HEADER, required = false)
                                                           String key) {
        return respond("credit-score", key, () -> body(
                "customerId", customerId,
                "score", 600 + (int) (customerId * 37 % 300),
                "band", customerId % 2 == 0 ? "B" : "A",
                "provider", "ErrorLens Credit Bureau (simulado)"));
    }

    @GetMapping("/customer-profile/{customerId}")
    public ResponseEntity<Map<String, Object>> customerProfile(@PathVariable long customerId,
                                                               @RequestHeader(name = KEY_HEADER, required = false)
                                                               String key) {
        return respond("customer-profile", key, () -> body(
                "customerId", customerId,
                "segment", customerId % 3 == 0 ? "PREMIUM" : "VAREJO",
                "preferredChannel", "EMAIL",
                "riskLevel", "BAIXO"));
    }

    @GetMapping("/recommendations/{customerId}")
    public ResponseEntity<Map<String, Object>> recommendations(@PathVariable long customerId,
                                                               @RequestHeader(name = KEY_HEADER, required = false)
                                                               String key) {
        return respond("recommendations", key, () -> body(
                "customerId", customerId,
                "items", List.of(
                        Map.of("sku", "SKU-DOCK-USBC", "title", "Dock station USB-C", "reason", "Compras recentes"),
                        Map.of("sku", "SKU-HEADSET-BT", "title", "Headset sem fio", "reason", "Clientes similares"))));
    }

    @PostMapping("/payments/authorize")
    public ResponseEntity<Map<String, Object>> authorize(@RequestBody(required = false) Map<String, Object> payment,
                                                         @RequestHeader(name = KEY_HEADER, required = false)
                                                         String key) {
        return respond("payments/authorize", key, () -> body(
                "authorizationId", "AUTH-" + Long.toHexString(System.nanoTime()).toUpperCase(),
                "status", "APPROVED",
                "sequence", payment == null ? null : payment.get("sequence")));
    }

    private ResponseEntity<Map<String, Object>> respond(String operation, String key,
                                                        java.util.function.Supplier<Map<String, Object>> success) {
        BehaviorRegistry.Decision decision = registry.decide(key);
        log.info("Chamada {} #{} (modo {}): status {} após {} ms", operation, decision.call(), decision.mode(),
                decision.status(), decision.delayMs());
        if (decision.delayMs() > 0) {
            try {
                Thread.sleep(decision.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (decision.status() >= 400) {
            log.warn("Falha simulada em {}: HTTP {}", operation, decision.status());
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(decision.status());
            if (decision.mode() == Behavior.Mode.UNAVAILABLE) {
                builder.header("Retry-After", "30");
            }
            return builder.body(body("error", decision.mode() == Behavior.Mode.UNAVAILABLE
                            ? "Serviço em manutenção (simulado)" : "Falha simulada",
                    "mode", decision.mode().name(), "call", decision.call(), "status", decision.status()));
        }
        return ResponseEntity.ok(success.get());
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            body.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        body.put("generatedAt", Instant.now().toString());
        return body;
    }
}
