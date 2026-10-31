package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Limite de concorrência do Scenario Runner atingido ou cenário com estado compartilhado em uso.
 */
public class ScenarioBusyException extends ErrorLensException {

    public ScenarioBusyException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "SCENARIO_BUSY", message);
    }
}
