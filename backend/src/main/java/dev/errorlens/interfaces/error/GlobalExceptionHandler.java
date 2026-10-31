package dev.errorlens.interfaces.error;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

import dev.errorlens.domain.exception.ErrorLensException;
import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.ExecutionTrace;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Tratamento global de exceptions. Todas as respostas seguem Problem Details (RFC 9457) com
 * extensões seguras (errorCode, correlationId, timestamp). Detalhes técnicos (stack trace, SQL,
 * mensagens internas) ficam disponíveis apenas nos endpoints de diagnóstico autorizados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailsFactory problems;

    public GlobalExceptionHandler(ProblemDetailsFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ErrorLensException.class)
    ResponseEntity<ProblemDetail> handleApplication(ErrorLensException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Falha de aplicação [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.warn("Requisição rejeitada [{}]: {}", ex.getErrorCode(), ex.getMessage());
        }
        return respond(ex, ex.getStatus(), null, ex.getMessage(), ex.getErrorCode(), request, "handleApplication");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        String errorCode;
        String detail;
        if (message.contains("uk_customers_email")) {
            errorCode = "CUSTOMER_EMAIL_DUPLICATED";
            detail = "Já existe um cliente com o e-mail informado.";
        } else if (message.contains("fk_orders_customer")) {
            errorCode = "CUSTOMER_HAS_ORDERS";
            detail = "O cliente possui pedidos associados e não pode ser excluído.";
        } else {
            errorCode = "DATA_INTEGRITY_VIOLATION";
            detail = "A operação viola uma regra de integridade dos dados.";
        }
        log.warn("Violação de integridade no banco [{}]", errorCode, ex);
        return respond(ex, HttpStatus.CONFLICT, null, detail, errorCode, request, "handleIntegrity");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                       HttpServletRequest request) {
        log.warn("Conflito de concorrência otimista", ex);
        return respond(ex, HttpStatus.CONFLICT, null, "O registro foi alterado por outra operação.",
                "CONCURRENT_MODIFICATION", request, "handleOptimisticLock");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Falha de persistência", ex);
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, null,
                "Não foi possível concluir a operação de persistência.", "PERSISTENCE_FAILURE", request,
                "handleDataAccess");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    ResponseEntity<ProblemDetail> handleCircuitOpen(CallNotPermittedException ex, HttpServletRequest request) {
        log.warn("Chamada rejeitada pelo Circuit Breaker: {}", ex.getMessage());
        return respond(ex, HttpStatus.SERVICE_UNAVAILABLE, null,
                "A dependência está temporariamente indisponível (circuito aberto).", "CIRCUIT_OPEN", request,
                "handleCircuitOpen");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        return respond(ex, HttpStatus.BAD_REQUEST, null, ex.getMessage(), "INVALID_ARGUMENT", request,
                "handleIllegalArgument");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        log.warn("Violação de restrição: {}", ex.getMessage());
        return respond(ex, HttpStatus.BAD_REQUEST, null, "Parâmetros inválidos.", "CONSTRAINT_VIOLATION", request,
                "handleConstraintViolation");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Falha de autenticação: {}", ex.getClass().getSimpleName());
        return respond(ex, HttpStatus.UNAUTHORIZED, "Unauthorized", "Credenciais inválidas.",
                "INVALID_CREDENTIALS", request, "handleAuthentication");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ex, HttpStatus.FORBIDDEN, "Forbidden", "Acesso negado.", "ACCESS_DENIED", request,
                "handleAccessDenied");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ex, HttpStatus.INTERNAL_SERVER_ERROR, null,
                "Ocorreu um erro interno. Use o correlationId para investigar.", "INTERNAL_ERROR", request,
                "handleUnexpected");
    }

    // --- Exceptions padrão do Spring MVC -----------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        log.warn("Validação falhou: {} campo(s) inválido(s)", errors.size());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Os dados enviados não são válidos.");
        problem.setProperty("errors", errors);
        return finish(ex, problem, headers, status, request, "VALIDATION_FAILED", "handleMethodArgumentNotValid");
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        log.warn("Corpo da requisição ilegível: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                "O corpo da requisição não pôde ser interpretado. Verifique a sintaxe do JSON.");
        return finish(ex, problem, headers, status, request, "MALFORMED_REQUEST_BODY",
                "handleHttpMessageNotReadable");
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        log.warn("Método {} não suportado; permitidos: {}", ex.getMethod(), ex.getSupportedHttpMethods());
        return super.handleHttpRequestMethodNotSupported(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail pd ? pd
                : ProblemDetail.forStatusAndDetail(statusCode, "Requisição inválida.");
        String errorCode = switch (ex) {
            case HttpRequestMethodNotSupportedException ignored -> "METHOD_NOT_ALLOWED";
            case MethodArgumentTypeMismatchException mismatch -> {
                problem.setDetail("O parâmetro '" + mismatch.getName() + "' possui formato inválido.");
                log.warn("Parâmetro '{}' com tipo incompatível", mismatch.getName());
                yield "INVALID_PARAMETER_TYPE";
            }
            case MaxUploadSizeExceededException ignored -> "PAYLOAD_TOO_LARGE";
            default -> "REQUEST_" + statusCode.value();
        };
        return finish(ex, problem, headers, statusCode, request, errorCode, "handleExceptionInternal");
    }

    // --- Utilitários --------------------------------------------------------------------------

    private ResponseEntity<Object> finish(Exception ex, ProblemDetail problem, HttpHeaders headers,
                                          HttpStatusCode status, WebRequest request, String errorCode,
                                          String handler) {
        HttpServletRequest servletRequest = request instanceof ServletWebRequest swr ? swr.getRequest() : null;
        problems.enrich(problem, errorCode, servletRequest);
        recordHandling(ex, status, errorCode, handler);
        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    private ResponseEntity<ProblemDetail> respond(Exception ex, HttpStatus status, String title, String detail,
                                                  String errorCode, HttpServletRequest request, String handler) {
        ProblemDetail problem = problems.create(status, title, detail, errorCode, request);
        recordHandling(ex, status, errorCode, handler);
        return ResponseEntity.status(status).body(problem);
    }

    private static void recordHandling(Exception ex, HttpStatusCode status, String errorCode, String handler) {
        ExecutionTrace trace = ExecutionContextHolder.currentTrace();
        if (trace == null) {
            return;
        }
        trace.recordHandledException(ex, errorCode);
        ExecutionContextHolder.event(StepLayer.EXCEPTION_HANDLER, "GlobalExceptionHandler." + handler,
                GlobalExceptionHandler.class.getName(),
                status.is5xxServerError() ? StepStatus.ERROR : StepStatus.WARN,
                ex.getClass().getSimpleName() + " -> HTTP " + status.value() + " (" + errorCode + ")", ex);
    }

    private static Map<String, Object> fieldError(FieldError error) {
        return Map.of("field", error.getField(),
                "message", String.valueOf(error.getDefaultMessage()),
                "code", String.valueOf(error.getCode()));
    }
}
