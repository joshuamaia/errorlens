package dev.errorlens.application.lab;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import dev.errorlens.config.AsyncConfig;
import dev.errorlens.domain.exception.ExternalDependencyException;
import dev.errorlens.domain.exception.ExternalDependencyTimeoutException;
import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.ExecutionTrace;
import dev.errorlens.infrastructure.resilience.ResilienceEventRecorder;
import dev.errorlens.infrastructure.simulator.DependencySimulatorClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

/**
 * Integrações com o dependency-simulator protegidas por Resilience4j: TimeLimiter (F01),
 * Retry (F02/F03), Circuit Breaker (F04) e fallback (F05). Cada chamada é registrada como
 * tentativa (ScenarioAttempt) com duração, status e estado do circuito.
 */
@Service
public class LabIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LabIntegrationService.class);
    private static final int MAX_CIRCUIT_DEMO_CALLS = 12;

    private final DependencySimulatorClient client;
    private final TimeLimiterRegistry timeLimiters;
    private final RetryRegistry retries;
    private final CircuitBreakerRegistry circuitBreakers;
    private final TaskExecutor executor;

    public LabIntegrationService(DependencySimulatorClient client, TimeLimiterRegistry timeLimiters,
                                 RetryRegistry retries, CircuitBreakerRegistry circuitBreakers,
                                 @Qualifier(AsyncConfig.DEPENDENCY_EXECUTOR) TaskExecutor executor) {
        this.client = client;
        this.timeLimiters = timeLimiters;
        this.retries = retries;
        this.circuitBreakers = circuitBreakers;
        this.executor = executor;
    }

    /**
     * F01: chamada executada em outra thread (com contexto propagado) e limitada pelo TimeLimiter.
     */
    public Map<String, Object> creditScore(long customerId, String simulationKey) {
        TimeLimiter timeLimiter = timeLimiters.timeLimiter(ResilienceEventRecorder.CREDIT_SCORE_TIME_LIMITER);
        long limitMs = timeLimiter.getTimeLimiterConfig().getTimeoutDuration().toMillis();
        log.info("Consultando score de crédito do cliente {} (limite {} ms)", customerId, limitMs);
        Instant start = Instant.now();
        try {
            Map<String, Object> result = timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(
                    () -> client.creditScore(customerId, simulationKey), executor::execute));
            recordAttempt("credit-score", start, "SUCCESS", 200, null, null, "Resposta dentro do limite");
            return result;
        } catch (TimeoutException ex) {
            recordAttempt("credit-score", start, "TIMEOUT", null, ex, null,
                    "Cancelada pelo TimeLimiter após " + limitMs + " ms");
            throw new ExternalDependencyTimeoutException(
                    "A consulta de score de crédito excedeu o limite de " + limitMs + " ms.", ex);
        } catch (RestClientException ex) {
            recordAttempt("credit-score", start, "FAILURE", statusOf(ex), ex, null, null);
            throw new ExternalDependencyException("Falha ao consultar o serviço de score de crédito.", ex);
        } catch (Exception ex) {
            recordAttempt("credit-score", start, "FAILURE", null, ex, null, null);
            throw new ExternalDependencyException("Falha inesperada ao consultar o score de crédito.", ex);
        }
    }

    /**
     * F02/F03: Retry com backoff exponencial; cada tentativa é registrada individualmente.
     */
    public Map<String, Object> customerProfile(long customerId, String simulationKey) {
        Retry retry = retries.retry(ResilienceEventRecorder.CUSTOMER_PROFILE_RETRY);
        int maxAttempts = retry.getRetryConfig().getMaxAttempts();
        log.info("Consultando perfil externo do cliente {} (até {} tentativas)", customerId, maxAttempts);
        try {
            return retry.executeSupplier(() -> attempt("customer-profile", null,
                    () -> client.customerProfile(customerId, simulationKey)));
        } catch (RestClientException ex) {
            log.error("Perfil externo indisponível após {} tentativas", maxAttempts);
            throw new ExternalDependencyException(
                    "O serviço de perfil de clientes permaneceu indisponível após " + maxAttempts + " tentativas.",
                    ex);
        }
    }

    /**
     * F04: demonstração completa dos estados CLOSED -> OPEN -> HALF_OPEN -> (CLOSED | OPEN).
     * Se o circuito terminar aberto, a última chamada rejeitada é propagada (HTTP 503).
     */
    public Map<String, Object> circuitBreakerDemo(String simulationKey) {
        CircuitBreaker circuit = circuitBreakers.circuitBreaker(ResilienceEventRecorder.PAYMENT_CIRCUIT);
        Duration openWait = Duration.ofMillis(
                circuit.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1));
        int halfOpenCalls = circuit.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState();
        List<String> states = new ArrayList<>();
        states.add(circuit.getState().name());
        int call = 0;

        log.info("Fase 1: chamadas ao gateway de pagamentos até o circuito abrir (estado inicial {})",
                circuit.getState());
        while (circuit.getState() == CircuitBreaker.State.CLOSED && call < 6) {
            invokePayment(circuit, simulationKey, ++call);
        }
        addState(states, circuit);

        if (circuit.getState() == CircuitBreaker.State.OPEN) {
            log.info("Fase 2: chamada com circuito OPEN deve ser rejeitada sem atingir a dependência");
            invokePayment(circuit, simulationKey, ++call);
            log.info("Fase 3: aguardando {} ms (waitDurationInOpenState)", openWait.toMillis() + 100);
            sleep(openWait.plusMillis(100));
            log.info("Fase 4: até {} chamadas de teste em HALF_OPEN", halfOpenCalls);
            for (int i = 0; i < halfOpenCalls && call < MAX_CIRCUIT_DEMO_CALLS; i++) {
                invokePayment(circuit, simulationKey, ++call);
                addState(states, circuit);
                if (circuit.getState() != CircuitBreaker.State.HALF_OPEN) {
                    break;
                }
            }
        }

        if (circuit.getState() == CircuitBreaker.State.OPEN) {
            log.error("Circuito permaneceu OPEN após as chamadas de teste; próxima chamada será rejeitada");
            int finalCall = ++call;
            Instant start = Instant.now();
            try {
                circuit.executeSupplier(() -> client.authorizePayment(simulationKey, finalCall));
            } catch (CallNotPermittedException ex) {
                recordAttempt("payments/authorize", start, "REJECTED", null, ex, circuit.getState().name(),
                        "Chamada " + finalCall + " rejeitada pelo circuito");
                throw ex;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("circuit", circuit.getName());
        result.put("finalState", circuit.getState().name());
        result.put("observedStates", states);
        result.put("calls", call);
        result.put("failureRate", circuit.getMetrics().getFailureRate());
        log.info("Demonstração concluída: estados observados {}", states);
        return result;
    }

    /**
     * F05: fallback controlado. A resposta degradada é sinalizada explicitamente.
     */
    public RecommendationResult recommendations(long customerId, String simulationKey) {
        CircuitBreaker circuit = circuitBreakers.circuitBreaker(ResilienceEventRecorder.RECOMMENDATIONS_CIRCUIT);
        try {
            Map<String, Object> body = circuit.executeSupplier(() -> attempt("recommendations", circuit,
                    () -> client.recommendations(customerId, simulationKey)));
            log.info("Recomendações personalizadas obtidas para o cliente {}", customerId);
            return new RecommendationResult(false, "PERSONALIZED", null, body.get("items"));
        } catch (RestClientException | CallNotPermittedException ex) {
            log.warn("Fallback acionado para recomendações do cliente {}: {}", customerId,
                    ex.getClass().getSimpleName());
            ExecutionContextHolder.event(StepLayer.RESILIENCE, "Fallback · resposta degradada",
                    "LabIntegrationService#recommendations", StepStatus.WARN,
                    "Dependência falhou (" + ex.getClass().getSimpleName()
                            + "); retornando recomendações padrão", ex);
            return new RecommendationResult(true, "FALLBACK", ex.getClass().getSimpleName(),
                    List.of(Map.of("sku", "SKU-MONITOR-34", "title", "Monitor UltraWide 34", "reason", "Mais vendido"),
                            Map.of("sku", "SKU-NOTEBOOK-14", "title", "Notebook Pro 14", "reason", "Mais vendido")));
        }
    }

    private void invokePayment(CircuitBreaker circuit, String simulationKey, int call) {
        try {
            circuit.executeSupplier(() -> attempt("payments/authorize", circuit,
                    () -> client.authorizePayment(simulationKey, call)));
            log.info("Chamada {} ao gateway de pagamentos bem-sucedida (estado {})", call, circuit.getState());
        } catch (CallNotPermittedException ex) {
            recordAttempt("payments/authorize", Instant.now(), "REJECTED", null, ex, circuit.getState().name(),
                    "Chamada " + call + " rejeitada pelo circuito");
        } catch (RestClientException ex) {
            log.warn("Chamada {} ao gateway de pagamentos falhou: {} (estado {})", call,
                    ex.getClass().getSimpleName(), circuit.getState());
        }
    }

    private <T> T attempt(String operation, CircuitBreaker circuit, Supplier<T> call) {
        Instant start = Instant.now();
        String state = circuit == null ? null : circuit.getState().name();
        try {
            T result = call.get();
            recordAttempt(operation, start, "SUCCESS", 200, null, state, null);
            return result;
        } catch (RuntimeException ex) {
            recordAttempt(operation, start, "FAILURE", statusOf(ex), ex, state, null);
            throw ex;
        }
    }

    private static void recordAttempt(String operation, Instant start, String outcome, Integer status,
                                      Throwable error, String circuitState, String detail) {
        ExecutionTrace trace = ExecutionContextHolder.currentTrace();
        if (trace != null) {
            trace.addAttempt(DependencySimulatorClient.TARGET, operation, start,
                    Duration.between(start, Instant.now()).toMillis(), outcome, status,
                    error == null ? null : error.getClass().getName(), circuitState, detail);
        }
    }

    private static Integer statusOf(Throwable ex) {
        return ex instanceof HttpStatusCodeException http ? http.getStatusCode().value() : null;
    }

    private static void addState(List<String> states, CircuitBreaker circuit) {
        String state = circuit.getState().name();
        if (!states.getLast().equals(state)) {
            states.add(state);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record RecommendationResult(boolean degraded, String source, String fallbackReason, Object items) {
    }
}
