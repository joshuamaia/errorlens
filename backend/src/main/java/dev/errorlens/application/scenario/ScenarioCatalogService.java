package dev.errorlens.application.scenario;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import dev.errorlens.application.execution.ComparisonService;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.persistence.execution.ScenarioRepository;

/**
 * Catálogo do Scenario Lab com estatísticas reais de uso e o recurso Compare Modes.
 */
@Service
public class ScenarioCatalogService {

    private final ScenarioRepository scenarios;
    private final ScenarioRegistry registry;
    private final ScenarioRunner runner;
    private final ComparisonService comparison;
    private final JdbcTemplate jdbc;

    public ScenarioCatalogService(ScenarioRepository scenarios, ScenarioRegistry registry, ScenarioRunner runner,
                                  ComparisonService comparison, JdbcTemplate jdbc) {
        this.scenarios = scenarios;
        this.registry = registry;
        this.runner = runner;
        this.comparison = comparison;
        this.jdbc = jdbc;
    }

    public List<ScenarioView> list() {
        Map<String, Stats> stats = stats();
        return scenarios.findByEnabledTrueOrderByDisplayOrderAsc().stream()
                .map(s -> view(s, stats.get(s.getCode())))
                .toList();
    }

    public ScenarioView get(String code) {
        Scenario scenario = scenarios.findById(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("SCENARIO_NOT_FOUND", "Cenário " + code
                        + " não encontrado."));
        return view(scenario, stats().get(scenario.getCode()));
    }

    /**
     * Executa as variantes de sucesso e de falha do mesmo cenário em sequência e as compara.
     */
    public CompareModesResult compareModes(String code, String triggeredBy) {
        ScenarioView scenario = get(code);
        if (!scenario.compareSupported()) {
            throw new InvalidInputException("COMPARE_NOT_SUPPORTED",
                    "O cenário " + scenario.code() + " não possui variantes de sucesso e falha.");
        }
        String group = "cmp-" + UUID.randomUUID().toString().substring(0, 13);
        Execution success = runner.execute(scenario.code(), ExecutionMode.SUCCESS, triggeredBy, group);
        Execution failure = runner.execute(scenario.code(), ExecutionMode.FAILURE, triggeredBy, group);
        return new CompareModesResult(group, success.getId(), failure.getId(),
                comparison.compare(success.getId(), failure.getId()));
    }

    private ScenarioView view(Scenario s, Stats stats) {
        Set<ExecutionMode> modes = registry.find(s.getCode()).map(ScenarioHandler::supportedModes)
                .orElse(Set.of());
        return new ScenarioView(s.getCode(), s.getName(), s.getDescription(), s.getCategory(), s.getGroupName(),
                s.getHttpMethod(), s.getEndpoint(), s.getExpectedStatus(), s.getExpectedOutcome(), s.getFailureType(),
                s.getDifficulty(), s.getExplanation(), s.tagList(), s.isCompareSupported() && modes.size() > 1,
                s.getSuccessVariant(), s.getFailureVariant(), modes.stream().map(Enum::name).sorted().toList(),
                registry.find(s.getCode()).isPresent(),
                stats == null ? 0 : stats.count(), stats == null ? null : stats.lastId(),
                stats == null ? null : stats.lastStatus(), stats == null ? null : stats.lastOutcome(),
                stats == null ? null : stats.lastAt());
    }

    private Map<String, Stats> stats() {
        Map<String, Stats> result = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (e.scenario_code) e.scenario_code, e.id, e.http_status, e.outcome, e.started_at,
                       count(*) OVER (PARTITION BY e.scenario_code) AS total
                FROM executions e
                ORDER BY e.scenario_code, e.started_at DESC, e.id DESC
                """, rs -> {
            Timestamp at = rs.getTimestamp("started_at");
            result.put(rs.getString("scenario_code"), new Stats(rs.getLong("total"), rs.getLong("id"),
                    (Integer) rs.getObject("http_status"), rs.getString("outcome"),
                    at == null ? null : at.toInstant()));
        });
        return result;
    }

    private record Stats(long count, Long lastId, Integer lastStatus, String lastOutcome, Instant lastAt) {
    }

    public record ScenarioView(String code, String name, String description, String category, String group,
                               String httpMethod, String endpoint, int expectedStatus, String expectedOutcome,
                               String failureType, String difficulty, String explanation, List<String> tags,
                               boolean compareSupported, String successVariant, String failureVariant,
                               List<String> supportedModes, boolean executable, long executionCount,
                               Long lastExecutionId, Integer lastStatus, String lastOutcome, Instant lastExecutedAt) {
    }

    public record CompareModesResult(String comparisonGroup, Long successExecutionId, Long failureExecutionId,
                                     ComparisonService.Comparison comparison) {
    }
}
