package dev.errorlens.infrastructure.observability;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;

/**
 * Associa a thread atual ao {@link ExecutionTrace} em andamento e controla a profundidade
 * (aninhamento) das etapas. Precisa ser limpo ao final de cada requisição/tarefa.
 */
public final class ExecutionContextHolder {

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private ExecutionContextHolder() {
    }

    public static void bind(ExecutionTrace trace, int baseDepth) {
        CONTEXT.set(new Context(trace, baseDepth));
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static ExecutionTrace currentTrace() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : ctx.trace;
    }

    public static boolean isActive() {
        return CONTEXT.get() != null;
    }

    public static StepRecord openStep(StepLayer layer, String name, String component, String detail) {
        Context ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        StepRecord step = ctx.trace.open(layer, name, component, ctx.depth, detail);
        if (step != null) {
            ctx.depth++;
        }
        return step;
    }

    public static void closeStep(StepRecord step, StepStatus status, String detail, Throwable error) {
        Context ctx = CONTEXT.get();
        if (ctx == null || step == null) {
            return;
        }
        ctx.trace.close(step, status, detail, error);
        ctx.depth = Math.max(0, ctx.depth - 1);
    }

    public static StepRecord event(StepLayer layer, String name, String component, StepStatus status, String detail,
                                   Throwable error) {
        Context ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        return ctx.trace.event(layer, name, component, ctx.depth, status, detail, error);
    }

    public static StepRecord timedEvent(StepLayer layer, String name, String component, StepStatus status,
                                        long durationMicros, String detail, String errorClass, String errorMessage) {
        Context ctx = CONTEXT.get();
        if (ctx == null) {
            return null;
        }
        return ctx.trace.timedEvent(layer, name, component, ctx.depth, status, durationMicros, detail, errorClass,
                errorMessage);
    }

    /**
     * Captura o contexto atual para propagação a outra thread.
     */
    public static Snapshot snapshot() {
        Context ctx = CONTEXT.get();
        return ctx == null ? null : new Snapshot(ctx.trace, ctx.depth);
    }

    public static void restore(Snapshot snapshot) {
        if (snapshot == null) {
            CONTEXT.remove();
        } else {
            CONTEXT.set(new Context(snapshot.trace(), snapshot.depth()));
        }
    }

    public record Snapshot(ExecutionTrace trace, int depth) {
    }

    private static final class Context {
        private final ExecutionTrace trace;
        private int depth;

        private Context(ExecutionTrace trace, int depth) {
            this.trace = trace;
            this.depth = depth;
        }
    }
}
