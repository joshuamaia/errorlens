package dev.errorlens.application.execution;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.execution.ExecutionViews.AttemptView;
import dev.errorlens.application.execution.ExecutionViews.DiagnosisView;
import dev.errorlens.application.execution.ExecutionViews.Detail;
import dev.errorlens.application.execution.ExecutionViews.LogView;
import dev.errorlens.application.execution.ExecutionViews.StepView;
import dev.errorlens.application.execution.ExecutionViews.Summary;
import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionStep;
import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.persistence.execution.ScenarioRepository;

/**
 * Converte entidades de execução nas views da API.
 */
@Component
public class ExecutionMapper {

    private final ScenarioRepository scenarios;
    private final JsonText json;
    private final ErrorLensProperties properties;
    private volatile Map<String, Scenario> scenarioCache;

    public ExecutionMapper(ScenarioRepository scenarios, JsonText json, ErrorLensProperties properties) {
        this.scenarios = scenarios;
        this.json = json;
        this.properties = properties;
    }

    public Summary summary(Execution e) {
        Scenario scenario = scenario(e.getScenarioCode());
        return new Summary(e.getId(), e.getScenarioCode(), scenario == null ? e.getScenarioCode() : scenario.getName(),
                e.getMode().name(), e.getCorrelationId(), e.getTraceId(), e.getHttpMethod(), e.getEndpoint(),
                e.getHttpStatus(), e.getOutcome().name(), e.getExpectedStatus(), e.isMatchedExpectation(),
                e.getStartedAt(), e.getDurationMs(), e.getErrorCode(), e.getExceptionClass(), e.getRootCauseClass(),
                e.getComparisonGroup());
    }

    public Detail detail(Execution e) {
        Scenario scenario = scenario(e.getScenarioCode());
        Set<String> components = new LinkedHashSet<>();
        e.getSteps().stream().map(ExecutionStep::getLayer).forEach(layer -> components.add(layer.label()));
        return new Detail(summary(e),
                scenario == null ? null : scenario.getCategory(),
                scenario == null ? null : scenario.getDescription(),
                json.map(e.getRequestHeaders()), e.getRequestBody(),
                json.map(e.getResponseHeaders()), e.getResponseBody(),
                e.getExceptionMessage(), e.getRootCauseMessage(), json.mapList(e.getExceptionChain()),
                e.getStackTrace(), e.getVerification() == null ? null : json.map(e.getVerification()),
                e.getDiagnoses().stream().map(this::diagnosis).toList(),
                e.getAttempts().stream().map(a -> new AttemptView(a.getAttemptNumber(), a.getTarget(), a.getOperation(),
                        a.getStartedAt(), a.getDurationMs(), a.getOutcome(), a.getHttpStatus(), a.getErrorClass(),
                        a.getCircuitState(), a.getDetail())).toList(),
                List.copyOf(components), e.getSteps().size(), e.getLogs().size(), e.getTriggeredBy(),
                e.getFinishedAt(),
                e.getTraceId() == null ? null : properties.health().jaegerPublicUrl() + "/trace/" + e.getTraceId(),
                DiagnosisEngine.DISCLAIMER);
    }

    public StepView step(ExecutionStep s) {
        return new StepView(s.getSeq(), s.getLayer().name(), s.getLayer().label(), s.getName(), s.getComponent(),
                s.getDepth(), s.getStatus().name(), s.getStartedAt(), s.getOffsetMicros(), s.getDurationMicros(),
                s.getDetail(), s.getErrorClass(), s.getErrorMessage());
    }

    public List<LogView> logs(Execution e) {
        return e.getLogs().stream().map(l -> new LogView(l.getSeq(), l.getLoggedAt(), l.getLevel(), l.getLogger(),
                l.getThread(), l.getMessage(), l.getExceptionClass())).toList();
    }

    public DiagnosisView diagnosis(dev.errorlens.domain.execution.DiagnosisRecord d) {
        return new DiagnosisView(d.getRank(), d.getRuleId(), d.getTitle(), d.getCategory(), d.getDescription(),
                d.getConfidence(), d.getConfidenceScore(), json.stringList(d.getPossibleCauses()),
                json.stringList(d.getEvidences()), json.stringList(d.getSuggestions()), json.stringList(d.getFixes()),
                d.getCodeExample());
    }

    public Scenario scenario(String code) {
        Map<String, Scenario> cache = scenarioCache;
        if (cache == null) {
            cache = scenarios.findAll().stream().collect(Collectors.toMap(Scenario::getCode, Function.identity()));
            scenarioCache = cache;
        }
        return cache.get(code);
    }
}
