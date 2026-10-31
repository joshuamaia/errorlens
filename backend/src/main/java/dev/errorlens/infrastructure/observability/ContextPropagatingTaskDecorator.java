package dev.errorlens.infrastructure.observability;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;

/**
 * Propaga para tarefas assíncronas: MDC (correlationId/executionKey), contexto de execução
 * do ErrorLens e contexto de observação/tracing do Micrometer. Restaura o estado anterior
 * da thread ao final para evitar vazamento de MDC entre tarefas do pool.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    private final ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder().build();

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        ExecutionContextHolder.Snapshot execution = ExecutionContextHolder.snapshot();
        ContextSnapshot snapshot = snapshotFactory.captureAll();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                ExecutionContextHolder.restore(execution);
                runnable.run();
            } finally {
                ExecutionContextHolder.clear();
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
