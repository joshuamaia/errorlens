package dev.errorlens.application.scenario;

import java.util.List;

/**
 * Agrupa handlers de cenários relacionados. Novos grupos são descobertos automaticamente
 * como beans do Spring.
 */
public interface ScenarioHandlerGroup {

    List<ScenarioHandler> handlers();
}
