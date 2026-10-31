package dev.errorlens.application.execution;

import java.time.Instant;

/**
 * Filtros do histórico de execuções. Todos são opcionais.
 *
 * @param q busca livre: correlationId, traceId, exception, código ou nome do cenário, ou "#id"
 */
public record ExecutionFilter(Instant from, Instant to, String outcome, Integer httpStatus, String scenario,
                              String correlationId, String exception, String q, String comparisonGroup) {
}
