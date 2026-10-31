package dev.errorlens.application.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.diagnosis.DiagnosisResult;
import dev.errorlens.application.scenario.LabRequestDispatcher.DispatchResult;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.domain.execution.DiagnosisRecord;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionLog;
import dev.errorlens.domain.execution.ExecutionOutcome;
import dev.errorlens.domain.execution.ExecutionStep;
import dev.errorlens.domain.execution.ScenarioAttempt;
import dev.errorlens.infrastructure.observability.ExecutionTrace;
import dev.errorlens.infrastructure.observability.MdcKeys;
import dev.errorlens.infrastructure.observability.SensitiveDataSanitizer;
import dev.errorlens.infrastructure.persistence.execution.ExecutionRepository;

/**
 * Converte o que foi observado em uma execução em registros persistidos (dados sanitizados),
 * executa o Diagnosis Engine e grava tudo em uma única transação.
 */
@Service
public class ExecutionPersistenceService {

    private static final int BODY_LIMIT = SensitiveDataSanitizer.DEFAULT_BODY_LIMIT;

    private final ExecutionRepository executions;
    private final DiagnosisEngine diagnosisEngine;
    private final DiagnosisContextFactory contextFactory;
    private final JsonText json;

    public ExecutionPersistenceService(ExecutionRepository executions, DiagnosisEngine diagnosisEngine,
                                       DiagnosisContextFactory contextFactory, JsonText json) {
        this.executions = executions;
        this.diagnosisEngine = diagnosisEngine;
        this.contextFactory = contextFactory;
        this.json = json;
    }

    @Transactional
    public Execution persist(ExecutionCapture capture) {
        ScenarioPlan plan = capture.plan();
        ExecutionTrace trace = capture.trace();
        DispatchResult result = capture.result();

        Execution execution = new Execution(capture.scenario().getCode(), capture.mode(), trace.correlationId(),
                plan != null ? plan.method() : capture.scenario().getHttpMethod(),
                plan != null ? plan.path() : capture.scenario().getEndpoint(),
                capture.startedAt());
        execution.setTraceId(trace.traceId());
        execution.setSpanId(trace.spanId());
        execution.setTriggeredBy(capture.triggeredBy());
        execution.setComparisonGroup(capture.comparisonGroup());
        execution.setFinishedAt(capture.startedAt().plusMillis(capture.totalMs()));
        execution.setDurationMs(capture.responseTimeMs() != null ? capture.responseTimeMs() : capture.totalMs());

        Integer status = result == null ? null : result.status();
        ExecutionOutcome outcome = classify(result);
        execution.setHttpStatus(status);
        execution.setOutcome(outcome);
        int expected = plan != null ? plan.expectedStatus() : capture.scenario().getExpectedStatus();
        execution.setExpectedStatus(expected);
        execution.setMatchedExpectation(status != null && status == expected && plan != null
                && outcome == plan.expectedOutcome());

        if (capture.sentHeaders() != null) {
            Map<String, List<String>> sent = new LinkedHashMap<>();
            capture.sentHeaders().forEach((k, v) -> sent.put(k, List.of(v)));
            execution.setRequestHeaders(json.write(SensitiveDataSanitizer.headers(sent)));
        }
        if (plan != null) {
            execution.setRequestBody(SensitiveDataSanitizer.body(plan.rawBody(), BODY_LIMIT));
        }
        if (result != null) {
            execution.setResponseHeaders(json.write(SensitiveDataSanitizer.headers(result.headers())));
            execution.setResponseBody(SensitiveDataSanitizer.body(result.body(), BODY_LIMIT));
        }

        Throwable exception = trace.handledException() != null ? trace.handledException() : capture.runnerError();
        String errorCode = trace.errorCode() != null ? trace.errorCode()
                : capture.runnerError() != null ? "RUNNER_FAILURE" : null;
        execution.setErrorCode(errorCode);
        if (exception != null) {
            List<ExceptionInfo> chain = ExceptionChains.chain(exception);
            execution.setExceptionClass(chain.getFirst().className());
            execution.setExceptionMessage(chain.getFirst().message());
            execution.setRootCauseClass(chain.getLast().className());
            execution.setRootCauseMessage(chain.getLast().message());
            execution.setExceptionChain(json.write(chain));
            execution.setStackTrace(ExceptionChains.stackTrace(exception));
        }
        if (capture.verification() != null) {
            execution.setVerification(json.write(capture.verification()));
        }

        trace.steps().forEach(step -> execution.addStep(new ExecutionStep(step.seq(), step.layer(), step.name(),
                step.component(), step.depth(), step.status(), step.startedAt(), step.offsetMicros(),
                step.durationMicros(), SensitiveDataSanitizer.text(step.detail(), 4000), step.errorClass(),
                step.errorMessage())));
        trace.logs().forEach(log -> execution.addLog(new ExecutionLog(log.seq(), log.timestamp(), log.level(),
                SensitiveDataSanitizer.truncate(log.logger(), 255), log.thread(), log.message(),
                log.exceptionClass())));
        trace.attempts().forEach(a -> execution.addAttempt(new ScenarioAttempt(a.number(), a.target(),
                a.operation(), a.startedAt(), a.durationMs(), a.outcome(), a.httpStatus(), a.errorClass(),
                a.circuitState(), a.detail())));

        List<DiagnosisResult> diagnoses = diagnosisEngine.diagnose(contextFactory.fromExecution(execution));
        int rank = 1;
        for (DiagnosisResult d : diagnoses) {
            execution.addDiagnosis(new DiagnosisRecord(rank++, d.ruleId(), d.title(), d.category(), d.description(),
                    d.confidence().name(), d.score(), json.write(d.possibleCauses()), json.write(d.evidences()),
                    json.write(d.suggestions()), json.write(d.fixes()), d.codeExample()));
        }
        return executions.save(execution);
    }

    static ExecutionOutcome classify(DispatchResult result) {
        if (result == null) {
            return ExecutionOutcome.FAILURE;
        }
        if (result.status() >= 200 && result.status() < 300) {
            return "true".equalsIgnoreCase(result.header(MdcKeys.DEGRADED_HEADER))
                    ? ExecutionOutcome.DEGRADED : ExecutionOutcome.SUCCESS;
        }
        return ExecutionOutcome.FAILURE;
    }
}
