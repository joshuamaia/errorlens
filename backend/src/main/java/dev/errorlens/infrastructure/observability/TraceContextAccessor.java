package dev.errorlens.infrastructure.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

/**
 * Lê traceId/spanId do span OpenTelemetry corrente (via Micrometer Tracing), quando disponível.
 */
@Component
public class TraceContextAccessor {

    private final ObjectProvider<Tracer> tracer;

    public TraceContextAccessor(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public String[] currentIds() {
        Tracer current = tracer.getIfAvailable();
        if (current == null) {
            return new String[] {null, null};
        }
        Span span = current.currentSpan();
        if (span == null || span.context() == null) {
            return new String[] {null, null};
        }
        return new String[] {span.context().traceId(), span.context().spanId()};
    }
}
