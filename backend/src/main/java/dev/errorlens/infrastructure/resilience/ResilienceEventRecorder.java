package dev.errorlens.infrastructure.resilience;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Assina os eventos do Resilience4j e os registra como etapas "Resilience4j" da execução ativa
 * (os eventos são publicados na thread chamadora), além de emitir logs correlacionados.
 */
@Component
public class ResilienceEventRecorder {

    public static final String CREDIT_SCORE_TIME_LIMITER = "creditScore";
    public static final String CUSTOMER_PROFILE_RETRY = "customerProfile";
    public static final String PAYMENT_CIRCUIT = "paymentGateway";
    public static final String RECOMMENDATIONS_CIRCUIT = "recommendations";

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventRecorder.class);

    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;

    public ResilienceEventRecorder(RetryRegistry retryRegistry, CircuitBreakerRegistry circuitBreakerRegistry,
                                   TimeLimiterRegistry timeLimiterRegistry) {
        this.retryRegistry = retryRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    @PostConstruct
    void subscribe() {
        Retry retry = retryRegistry.retry(CUSTOMER_PROFILE_RETRY);
        retry.getEventPublisher()
                .onRetry(event -> {
                    String detail = "Tentativa " + event.getNumberOfRetryAttempts() + " falhou ("
                            + simpleName(event.getLastThrowable()) + "); nova tentativa em "
                            + event.getWaitInterval().toMillis() + " ms";
                    log.warn("Retry '{}': {}", event.getName(), detail);
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Retry · nova tentativa agendada",
                            "Resilience4j Retry '" + event.getName() + "'", StepStatus.WARN, detail, null);
                })
                .onSuccess(event -> {
                    String detail = "Sucesso após " + (event.getNumberOfRetryAttempts() + 1) + " tentativa(s)";
                    log.info("Retry '{}': {}", event.getName(), detail);
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Retry · recuperado",
                            "Resilience4j Retry '" + event.getName() + "'", StepStatus.OK, detail, null);
                })
                .onError(event -> {
                    String detail = "Tentativas esgotadas: " + event.getNumberOfRetryAttempts()
                            + " tentativa(s); última falha: " + simpleName(event.getLastThrowable());
                    log.error("Retry '{}': {}", event.getName(), detail);
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Retry · esgotado",
                            "Resilience4j Retry '" + event.getName() + "'", StepStatus.ERROR, detail,
                            event.getLastThrowable());
                });

        subscribeCircuit(circuitBreakerRegistry.circuitBreaker(PAYMENT_CIRCUIT));
        subscribeCircuit(circuitBreakerRegistry.circuitBreaker(RECOMMENDATIONS_CIRCUIT));

        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(CREDIT_SCORE_TIME_LIMITER);
        timeLimiter.getEventPublisher()
                .onTimeout(event -> {
                    String detail = "Limite de " + timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis()
                            + " ms excedido; a espera foi cancelada";
                    log.error("TimeLimiter '{}': {}", event.getTimeLimiterName(), detail);
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "TimeLimiter · timeout",
                            "Resilience4j TimeLimiter '" + event.getTimeLimiterName() + "'", StepStatus.ERROR,
                            detail, null);
                })
                .onSuccess(event -> ExecutionContextHolder.event(StepLayer.RESILIENCE, "TimeLimiter · dentro do limite",
                        "Resilience4j TimeLimiter '" + event.getTimeLimiterName() + "'", StepStatus.OK,
                        "Resposta recebida antes do limite", null));
    }

    private void subscribeCircuit(CircuitBreaker circuitBreaker) {
        String component = "Resilience4j CircuitBreaker '" + circuitBreaker.getName() + "'";
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    var transition = event.getStateTransition();
                    String detail = transition.getFromState() + " -> " + transition.getToState();
                    log.warn("CircuitBreaker '{}': transição {}", event.getCircuitBreakerName(), detail);
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Circuit Breaker · " + transition.getToState(),
                            component, transition.getToState() == CircuitBreaker.State.OPEN ? StepStatus.ERROR
                                    : StepStatus.INFO, "Transição de estado: " + detail, null);
                })
                .onCallNotPermitted(event -> {
                    log.warn("CircuitBreaker '{}': chamada rejeitada (circuito OPEN)", event.getCircuitBreakerName());
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Circuit Breaker · chamada rejeitada",
                            component, StepStatus.REJECTED,
                            "CallNotPermittedException: a dependência não foi chamada", null);
                })
                .onReset(event -> {
                    log.info("CircuitBreaker '{}' reiniciado", event.getCircuitBreakerName());
                    ExecutionContextHolder.event(StepLayer.RESILIENCE, "Circuit Breaker · reset", component,
                            StepStatus.INFO, "Estado e métricas reiniciados (CLOSED)", null);
                });
    }

    private static String simpleName(Throwable throwable) {
        return throwable == null ? "desconhecida" : throwable.getClass().getSimpleName();
    }
}
