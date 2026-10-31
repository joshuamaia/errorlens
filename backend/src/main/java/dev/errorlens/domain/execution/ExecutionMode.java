package dev.errorlens.domain.execution;

/**
 * Modo de execução de um cenário. DEFAULT executa o comportamento padrão do cenário;
 * SUCCESS e FAILURE são as variantes usadas pelo recurso Compare Modes.
 */
public enum ExecutionMode {
    DEFAULT, SUCCESS, FAILURE
}
