package dev.errorlens.application.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.errorlens.application.diagnosis.DiagnosisContext;
import dev.errorlens.application.diagnosis.DiagnosisContext.AttemptInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.LogInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.StepInfo;
import dev.errorlens.domain.execution.Execution;

/**
 * Monta o contexto de evidências do Diagnosis Engine a partir de uma execução persistida.
 */
@Component
public class DiagnosisContextFactory {

    private final JsonText json;

    public DiagnosisContextFactory(JsonText json) {
        this.json = json;
    }

    public DiagnosisContext fromExecution(Execution execution) {
        List<ExceptionInfo> exceptions = json.mapList(execution.getExceptionChain()).stream()
                .map(m -> new ExceptionInfo(String.valueOf(m.get("className")),
                        m.get("message") == null ? null : String.valueOf(m.get("message"))))
                .toList();
        List<StepInfo> steps = execution.getSteps().stream()
                .map(s -> new StepInfo(s.getLayer().name(), s.getName(), s.getStatus().name(), s.getErrorClass(),
                        s.getErrorMessage(), s.getDetail()))
                .toList();
        List<LogInfo> logs = execution.getLogs().stream()
                .map(l -> new LogInfo(l.getLevel(), l.getMessage(), l.getExceptionClass()))
                .toList();
        List<AttemptInfo> attempts = execution.getAttempts().stream()
                .map(a -> new AttemptInfo(a.getAttemptNumber(), a.getOutcome(), a.getHttpStatus(), a.getErrorClass(),
                        a.getCircuitState()))
                .toList();
        Map<String, String> headers = new LinkedHashMap<>();
        json.map(execution.getResponseHeaders()).forEach((k, v) -> headers.put(k, String.valueOf(v)));
        return new DiagnosisContext(DiagnosisContext.Source.EXECUTION, execution.getHttpStatus(),
                execution.getErrorCode(), exceptions, steps, logs, attempts, execution.getOutcome().name(),
                execution.getDurationMs(), headers);
    }
}
