package dev.errorlens.infrastructure.security;

import java.io.IOException;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.StepRecord;

/**
 * Par de filtros que delimita a etapa "Spring Security" da timeline: a entrada fica no
 * início da cadeia de segurança e a saída logo após o AuthorizationFilter. Se a requisição
 * for barrada, o EntryPoint/AccessDeniedHandler encerra a etapa com erro.
 */
public final class SecurityStepFilters {

    static final String STEP_ATTRIBUTE = SecurityStepFilters.class.getName() + ".step";

    private SecurityStepFilters() {
    }

    public static void fail(HttpServletRequest request, Exception exception, String detail) {
        StepRecord step = (StepRecord) request.getAttribute(STEP_ATTRIBUTE);
        if (step != null && step.isOpen()) {
            ExecutionContextHolder.closeStep(step, StepStatus.ERROR, detail, exception);
        }
    }

    public static class Entry extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!ExecutionContextHolder.isActive()) {
                chain.doFilter(request, response);
                return;
            }
            String header = request.getHeader("Authorization");
            String mechanism = header == null ? "nenhuma credencial enviada"
                    : header.startsWith("Basic ") ? "HTTP Basic" : header.startsWith("Bearer ") ? "Bearer token"
                    : "esquema desconhecido";
            StepRecord step = ExecutionContextHolder.openStep(StepLayer.SECURITY, "Spring Security",
                    "SecurityFilterChain", "Autenticação: " + mechanism);
            request.setAttribute(STEP_ATTRIBUTE, step);
            try {
                chain.doFilter(request, response);
            } finally {
                if (step != null && step.isOpen()) {
                    StepStatus status = response.getStatus() >= 400 ? StepStatus.ERROR : StepStatus.OK;
                    ExecutionContextHolder.closeStep(step, status, null, null);
                }
            }
        }
    }

    public static class Exit extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            StepRecord step = (StepRecord) request.getAttribute(STEP_ATTRIBUTE);
            if (step != null && step.isOpen()) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                String who = auth == null ? "anônimo" : auth.getName() + " " + auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority).collect(Collectors.joining(", ", "[", "]"));
                ExecutionContextHolder.closeStep(step, StepStatus.OK,
                        step.detail() + " • Autenticado como " + who + " • Acesso autorizado", null);
            }
            chain.doFilter(request, response);
        }
    }
}
