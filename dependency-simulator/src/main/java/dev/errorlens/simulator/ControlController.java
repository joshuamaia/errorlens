package dev.errorlens.simulator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de controle do laboratório: configuram comportamentos e fazem reset.
 * Disponíveis apenas no perfil {@code lab} e protegidos por token compartilhado.
 */
@RestController
@Profile("lab")
@RequestMapping("/internal/control")
public class ControlController {

    private static final Logger log = LoggerFactory.getLogger(ControlController.class);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final BehaviorRegistry registry;
    private final SimulatorProperties properties;

    public ControlController(BehaviorRegistry registry, SimulatorProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PutMapping("/behaviors/{key}")
    public ResponseEntity<Map<String, Object>> configure(@PathVariable String key,
                                                         @Valid @RequestBody Behavior behavior,
                                                         @RequestHeader(name = "X-Lab-Control-Token",
                                                                 required = false) String token) {
        ResponseEntity<Map<String, Object>> denied = check(token, key);
        if (denied != null) {
            return denied;
        }
        if (!registry.configure(key, behavior)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Limite de comportamentos configurados atingido"));
        }
        log.info("Comportamento configurado para a chave {}: {}", key, behavior);
        return ResponseEntity.ok(Map.of("key", key, "behavior", behavior));
    }

    @GetMapping("/behaviors/{key}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String key,
                                                   @RequestHeader(name = "X-Lab-Control-Token", required = false)
                                                   String token) {
        ResponseEntity<Map<String, Object>> denied = check(token, key);
        if (denied != null) {
            return denied;
        }
        return registry.find(key)
                .map(state -> ResponseEntity.ok(Map.<String, Object>of("key", key, "behavior", state.behavior(),
                        "calls", state.calls().get(), "createdAt", state.createdAt().toString())))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/behaviors/{key}")
    public ResponseEntity<Void> remove(@PathVariable String key,
                                       @RequestHeader(name = "X-Lab-Control-Token", required = false) String token) {
        if (check(token, key) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        registry.remove(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@RequestHeader(name = "X-Lab-Control-Token", required = false)
                                                     String token) {
        if (!validToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token de controle inválido"));
        }
        int removed = registry.reset();
        log.info("Simulador reiniciado: {} comportamento(s) removido(s)", removed);
        return ResponseEntity.ok(Map.of("removed", removed));
    }

    private ResponseEntity<Map<String, Object>> check(String token, String key) {
        if (!validToken(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Token de controle inválido"));
        }
        if (!KEY.matcher(key).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Chave de simulação inválida"));
        }
        return null;
    }

    private boolean validToken(String token) {
        return token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                properties.controlToken().getBytes(StandardCharsets.UTF_8));
    }
}
