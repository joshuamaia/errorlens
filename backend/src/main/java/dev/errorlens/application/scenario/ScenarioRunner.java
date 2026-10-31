package dev.errorlens.application.scenario;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import dev.errorlens.application.execution.ExecutionCapture;
import dev.errorlens.application.execution.ExecutionPersistenceService;
import dev.errorlens.application.scenario.LabRequestDispatcher.DispatchResult;
import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.exception.ScenarioBusyException;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.ExecutionRecorder;
import dev.errorlens.infrastructure.observability.ExecutionTrace;
import dev.errorlens.infrastructure.observability.MdcKeys;
import dev.errorlens.infrastructure.observability.StepRecord;
import dev.errorlens.infrastructure.persistence.execution.ScenarioRepository;
import dev.errorlens.infrastructure.security.LabIdentities;
import dev.errorlens.infrastructure.simulator.SimulatorControlClient;

/**
 * Orquestra a execução real de um cenário: prepara dados, configura o simulador, envia a
 * requisição HTTP ao backend, verifica o resultado no banco e persiste tudo o que foi observado.
 * A concorrência é limitada por um semáforo e cenários com estado compartilhado são exclusivos.
 */
@Service
public class ScenarioRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

    private final ScenarioRepository scenarios;
    private final ScenarioRegistry registry;
    private final ScenarioSupport support;
    private final LabRequestDispatcher dispatcher;
    private final SimulatorControlClient simulatorControl;
    private final ExecutionRecorder recorder;
    private final ExecutionPersistenceService persistence;
    private final LabIdentities identities;
    private final ErrorLensProperties properties;
    private final Semaphore permits;
    private final Map<String, ReentrantLock> exclusiveLocks = new ConcurrentHashMap<>();

    public ScenarioRunner(ScenarioRepository scenarios, ScenarioRegistry registry, ScenarioSupport support,
                          LabRequestDispatcher dispatcher, SimulatorControlClient simulatorControl,
                          ExecutionRecorder recorder, ExecutionPersistenceService persistence,
                          LabIdentities identities, ErrorLensProperties properties) {
        this.scenarios = scenarios;
        this.registry = registry;
        this.support = support;
        this.dispatcher = dispatcher;
        this.simulatorControl = simulatorControl;
        this.recorder = recorder;
        this.persistence = persistence;
        this.identities = identities;
        this.properties = properties;
        this.permits = new Semaphore(Math.max(1, properties.runner().maxConcurrentExecutions()));
    }

    public Execution execute(String code, ExecutionMode mode, String triggeredBy, String comparisonGroup) {
        if (!properties.lab().enabled()) {
            throw new InvalidInputException(org.springframework.http.HttpStatus.FORBIDDEN, "LAB_DISABLED",
                    "A execução de cenários está desabilitada fora do perfil de laboratório.");
        }
        Scenario scenario = scenarios.findById(code).filter(Scenario::isEnabled)
                .orElseThrow(() -> new ResourceNotFoundException("SCENARIO_NOT_FOUND", "Cenário " + code
                        + " não encontrado."));
        ScenarioHandler handler = registry.find(code)
                .orElseThrow(() -> new ResourceNotFoundException("SCENARIO_HANDLER_NOT_FOUND",
                        "Cenário " + code + " não possui comportamento executável."));
        if (!handler.supportedModes().contains(mode)) {
            throw new InvalidInputException("MODE_NOT_SUPPORTED",
                    "O cenário " + code + " não suporta o modo " + mode + ".");
        }
        acquire(permits, "Limite de execuções simultâneas atingido. Tente novamente em instantes.");
        ReentrantLock lock = handler.exclusiveResource() == null ? null
                : exclusiveLocks.computeIfAbsent(handler.exclusiveResource(), k -> new ReentrantLock());
        try {
            if (lock != null) {
                acquire(lock, "O recurso compartilhado '" + handler.exclusiveResource()
                        + "' está em uso por outra execução.");
            }
            try {
                return run(scenario, handler, mode, triggeredBy, comparisonGroup);
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        } finally {
            permits.release();
        }
    }

    private Execution run(Scenario scenario, ScenarioHandler handler, ExecutionMode mode, String triggeredBy,
                          String comparisonGroup) {
        String correlationId = UUID.randomUUID().toString();
        ExecutionTrace trace = recorder.start(correlationId);
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        MDC.put(MdcKeys.EXECUTION_KEY, trace.key());
        MDC.put(MdcKeys.SCENARIO, scenario.getCode());
        ExecutionContextHolder.bind(trace, 0);

        Instant startedAt = Instant.now();
        long start = System.nanoTime();
        ScenarioPlan plan = null;
        DispatchResult result = null;
        Verification verification = null;
        Throwable runnerError = null;
        Map<String, String> headers = null;
        Long responseTimeMs = null;
        String simulationKey = null;
        try {
            log.info("Executando cenário {} - {} (modo {})", scenario.getCode(), scenario.getName(), mode);
            StepRecord prepare = ExecutionContextHolder.openStep(StepLayer.RUNNER, "Preparação do cenário",
                    "ScenarioRunner", scenario.getCode() + " • modo " + mode);
            try {
                plan = handler.plan(mode, support);
                if (plan.simulator() != null) {
                    simulationKey = trace.key();
                    simulatorControl.configure(simulationKey, plan.simulator());
                    log.info("Dependency simulator configurado: {}", plan.simulator());
                }
                ExecutionContextHolder.closeStep(prepare, StepStatus.OK, plan.variant(), null);
            } catch (RuntimeException ex) {
                ExecutionContextHolder.closeStep(prepare, StepStatus.ERROR, "Falha na preparação do cenário", ex);
                throw ex;
            }

            headers = requestHeaders(plan, correlationId, trace.key(), simulationKey);
            StepRecord dispatch = ExecutionContextHolder.openStep(StepLayer.RUNNER, "Envio da requisição HTTP",
                    "LabRequestDispatcher (RestClient)", plan.method() + " " + plan.path());
            long dispatchStart = System.nanoTime();
            try {
                result = dispatcher.dispatch(plan, headers);
                responseTimeMs = (System.nanoTime() - dispatchStart) / 1_000_000;
                StepStatus status = result.status() >= 500 ? StepStatus.ERROR
                        : result.status() >= 400 ? StepStatus.WARN : StepStatus.OK;
                ExecutionContextHolder.closeStep(dispatch, status, "Resposta recebida: HTTP " + result.status()
                        + " (esperado " + plan.expectedStatus() + ")", null);
            } catch (RuntimeException ex) {
                ExecutionContextHolder.closeStep(dispatch, StepStatus.ERROR, "Falha de comunicação HTTP", ex);
                throw ex;
            }
            trace.awaitInnerCompletion(3000);

            if (plan.verification() != null) {
                StepRecord verify = ExecutionContextHolder.openStep(StepLayer.VERIFICATION,
                        "Verificação automática", "ScenarioRunner", null);
                verification = plan.verification().get();
                log.info("{}: {}", verification.name(), verification.conclusion());
                ExecutionContextHolder.closeStep(verify, verification.passed() ? StepStatus.OK : StepStatus.ERROR,
                        verification.name() + ": " + verification.conclusion(), null);
            }
            log.info("Cenário {} concluído com HTTP {} em {} ms", scenario.getCode(), result.status(),
                    responseTimeMs);
        } catch (RuntimeException ex) {
            runnerError = ex;
            log.error("Falha do Scenario Runner ao executar {}: {}", scenario.getCode(), ex.getMessage(), ex);
        } finally {
            if (simulationKey != null) {
                removeSimulation(simulationKey);
            }
            ExecutionContextHolder.clear();
            recorder.finish(trace);
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
        }
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        return persistence.persist(new ExecutionCapture(scenario, mode, plan, trace, headers, result, verification,
                runnerError, startedAt, totalMs, responseTimeMs, triggeredBy, comparisonGroup));
    }

    private Map<String, String> requestHeaders(ScenarioPlan plan, String correlationId, String executionKey,
                                               String simulationKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put(MdcKeys.CORRELATION_HEADER, correlationId);
        headers.put(MdcKeys.EXECUTION_HEADER, executionKey);
        if (plan.identity() != null) {
            headers.put("Authorization", identities.basicHeader(plan.identity()));
        }
        if (simulationKey != null) {
            headers.put(MdcKeys.SIMULATION_KEY_HEADER, simulationKey);
        }
        headers.putAll(plan.headers());
        return headers;
    }

    private void removeSimulation(String key) {
        try {
            simulatorControl.remove(key);
        } catch (RuntimeException ex) {
            log.warn("Não foi possível remover a configuração de simulação {}: {}", key, ex.getMessage());
        }
    }

    private static void acquire(Semaphore semaphore, String message) {
        try {
            if (!semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                throw new ScenarioBusyException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScenarioBusyException(message);
        }
    }

    private static void acquire(ReentrantLock lock, String message) {
        try {
            if (!lock.tryLock(15, TimeUnit.SECONDS)) {
                throw new ScenarioBusyException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScenarioBusyException(message);
        }
    }
}
