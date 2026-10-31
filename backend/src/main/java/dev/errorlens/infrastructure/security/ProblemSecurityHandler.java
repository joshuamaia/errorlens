package dev.errorlens.infrastructure.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.ExecutionTrace;
import dev.errorlens.interfaces.error.ProblemDetailsFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responde 401/403 no formato Problem Details e registra a falha na execução instrumentada.
 * O desafio {@code WWW-Authenticate: Basic} só é enviado nos endpoints de laboratório, para
 * evitar o popup nativo de login no navegador nas chamadas do frontend.
 */
@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemSecurityHandler.class);

    private final ProblemDetailsFactory problems;
    private final JsonMapper jsonMapper;

    public ProblemSecurityHandler(ProblemDetailsFactory problems, JsonMapper jsonMapper) {
        this.problems = problems;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        log.warn("Acesso não autenticado negado a {} {}: {}", request.getMethod(), request.getRequestURI(),
                exception.getClass().getSimpleName());
        record(request, exception, "AUTHENTICATION_REQUIRED", "AuthenticationEntryPoint -> 401");
        if (request.getRequestURI().startsWith("/api/v1/lab/")) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"ErrorLens Lab\"");
        } else {
            response.setHeader("WWW-Authenticate", "Bearer realm=\"ErrorLens\"");
        }
        write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Autenticação necessária para acessar este recurso.", "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.warn("Acesso negado a {} {} para o usuário autenticado: {}", request.getMethod(),
                request.getRequestURI(), exception.getClass().getSimpleName());
        record(request, exception, "ACCESS_DENIED", "AccessDeniedHandler -> 403");
        write(request, response, HttpStatus.FORBIDDEN, "Forbidden",
                "A identidade autenticada não possui permissão para este recurso.", "ACCESS_DENIED");
    }

    private void record(HttpServletRequest request, Exception exception, String errorCode, String detail) {
        SecurityStepFilters.fail(request, exception, detail);
        ExecutionTrace trace = ExecutionContextHolder.currentTrace();
        if (trace != null) {
            trace.recordHandledException(exception, errorCode);
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String title,
                       String detail, String errorCode) throws IOException {
        ProblemDetail problem = problems.create(status, title, detail, errorCode, request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(problems.toMap(problem)));
    }
}
