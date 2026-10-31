package dev.errorlens.infrastructure.observability;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;

/**
 * Instrumenta a requisição de laboratório disparada pelo Scenario Runner. Atua somente em
 * {@code /api/v1/lab/**} e somente quando o header de execução corresponde a um trace ativo
 * registrado pelo próprio backend. Registra as etapas "HTTP Request" e "HTTP Response".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExecutionCaptureFilter extends OncePerRequestFilter {

    /** Profundidade base das etapas internas (abaixo da etapa de despacho do runner). */
    static final int INNER_BASE_DEPTH = 1;

    private final ExecutionRecorder recorder;
    private final TraceContextAccessor traceContext;

    public ExecutionCaptureFilter(ExecutionRecorder recorder, TraceContextAccessor traceContext) {
        this.recorder = recorder;
        this.traceContext = traceContext;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/lab/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ExecutionTrace trace = recorder.find(request.getHeader(MdcKeys.EXECUTION_HEADER));
        if (trace == null) {
            chain.doFilter(request, response);
            return;
        }
        String[] ids = traceContext.currentIds();
        trace.recordTraceContext(ids[0], ids[1]);
        ExecutionContextHolder.bind(trace, INNER_BASE_DEPTH);
        MDC.put(MdcKeys.EXECUTION_KEY, trace.key());
        String target = request.getMethod() + " " + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        StepRecord httpStep = ExecutionContextHolder.openStep(StepLayer.HTTP, "HTTP Request",
                "Tomcat / DispatcherServlet", target);
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            int status = response.getStatus();
            StepStatus stepStatus = failure != null || status >= 500 ? StepStatus.ERROR
                    : status >= 400 ? StepStatus.WARN : StepStatus.OK;
            ExecutionContextHolder.closeStep(httpStep, stepStatus, target + " -> HTTP " + status, failure);
            ExecutionContextHolder.event(StepLayer.RESPONSE, "HTTP Response", "HttpServletResponse", stepStatus,
                    "Status " + status + (response.getContentType() == null ? "" : " • " + response.getContentType()),
                    null);
            trace.markInnerCompleted();
            MDC.remove(MdcKeys.EXECUTION_KEY);
            ExecutionContextHolder.clear();
        }
    }
}
