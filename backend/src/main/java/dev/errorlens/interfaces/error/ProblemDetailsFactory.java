package dev.errorlens.interfaces.error;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import dev.errorlens.infrastructure.observability.CorrelationIdFilter;

/**
 * Cria respostas Problem Details (RFC 9457) padronizadas com extensões seguras:
 * errorCode, correlationId e timestamp. Nunca inclui stack traces ou detalhes internos.
 */
@Component
public class ProblemDetailsFactory {

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    public ProblemDetail create(HttpStatusCode status, String title, String detail, String errorCode,
                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (title != null) {
            problem.setTitle(title);
        }
        enrich(problem, errorCode, request);
        return problem;
    }

    public void enrich(ProblemDetail problem, String errorCode, HttpServletRequest request) {
        if (problem.getType() == null) {
            problem.setType(ABOUT_BLANK);
        }
        if (problem.getInstance() == null && request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("correlationId", request == null ? null : CorrelationIdFilter.current(request));
        problem.setProperty("timestamp", Instant.now().toString());
    }

    /**
     * Representação plana (formato RFC 9457) para escrita direta na resposta, fora do Spring MVC.
     */
    public Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType() == null ? "about:blank" : problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        body.put("instance", problem.getInstance() == null ? null : problem.getInstance().toString());
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
