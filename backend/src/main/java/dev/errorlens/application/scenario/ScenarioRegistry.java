package dev.errorlens.application.scenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.persistence.execution.ScenarioRepository;

/**
 * Indexa os handlers por código e valida, na inicialização, que todo cenário do catálogo
 * possui comportamento executável (e vice-versa).
 */
@Component
public class ScenarioRegistry {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRegistry.class);

    private final Map<String, ScenarioHandler> handlers = new HashMap<>();
    private final ScenarioRepository scenarios;

    public ScenarioRegistry(List<ScenarioHandlerGroup> groups, ScenarioRepository scenarios) {
        this.scenarios = scenarios;
        for (ScenarioHandlerGroup group : groups) {
            for (ScenarioHandler handler : group.handlers()) {
                if (handlers.put(handler.code(), handler) != null) {
                    throw new IllegalStateException("Handler duplicado para o cenário " + handler.code());
                }
            }
        }
    }

    public Optional<ScenarioHandler> find(String code) {
        return Optional.ofNullable(handlers.get(code));
    }

    public Set<String> codes() {
        return new TreeSet<>(handlers.keySet());
    }

    @EventListener(ApplicationReadyEvent.class)
    void validateCatalog() {
        Set<String> catalog = new TreeSet<>(scenarios.findAll().stream().map(Scenario::getCode).toList());
        Set<String> missingHandlers = new TreeSet<>(catalog);
        missingHandlers.removeAll(handlers.keySet());
        Set<String> orphanHandlers = new TreeSet<>(handlers.keySet());
        orphanHandlers.removeAll(catalog);
        if (!missingHandlers.isEmpty() || !orphanHandlers.isEmpty()) {
            log.error("Catálogo inconsistente. Cenários sem handler: {}; handlers sem cenário: {}", missingHandlers,
                    orphanHandlers);
        } else {
            log.info("Scenario Lab pronto com {} cenários executáveis", catalog.size());
        }
    }
}
