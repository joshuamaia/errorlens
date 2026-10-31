package dev.errorlens.domain.execution;

/**
 * Classificação do resultado observado de uma execução.
 * DEGRADED representa uma resposta HTTP de sucesso produzida por fallback.
 */
public enum ExecutionOutcome {
    SUCCESS, FAILURE, DEGRADED
}
