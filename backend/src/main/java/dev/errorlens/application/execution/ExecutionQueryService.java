package dev.errorlens.application.execution;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.application.execution.ExecutionViews.Detail;
import dev.errorlens.application.execution.ExecutionViews.LogView;
import dev.errorlens.application.execution.ExecutionViews.Stage;
import dev.errorlens.application.execution.ExecutionViews.StepView;
import dev.errorlens.application.execution.ExecutionViews.Summary;
import dev.errorlens.application.execution.ExecutionViews.Timeline;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionOutcome;
import dev.errorlens.domain.execution.ExecutionStep;
import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.persistence.execution.ExecutionRepository;

/**
 * Consultas do histórico, detalhes, logs e timeline das execuções.
 */
@Service
@Transactional(readOnly = true)
public class ExecutionQueryService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionQueryService.class);
    private static final Set<String> SORTABLE = Set.of("startedAt", "durationMs", "httpStatus", "id", "scenarioCode");
    private static final int MAX_PAGE_SIZE = 100;

    private final ExecutionRepository executions;
    private final ExecutionMapper mapper;

    public ExecutionQueryService(ExecutionRepository executions, ExecutionMapper mapper) {
        this.executions = executions;
        this.mapper = mapper;
    }

    public Page<Summary> search(ExecutionFilter filter, int page, int size, String sort, String direction) {
        String property = sort != null && SORTABLE.contains(sort) ? sort : "startedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id")));
        return executions.findAll(specification(filter), pageable).map(mapper::summary);
    }

    public Detail detail(Long id) {
        return mapper.detail(require(id));
    }

    public Detail detailByCorrelation(String correlationId) {
        return mapper.detail(executions.findByCorrelationId(correlationId)
                .orElseThrow(() -> new ResourceNotFoundException("EXECUTION_NOT_FOUND",
                        "Nenhuma execução com o correlationId informado.")));
    }

    public List<LogView> logs(Long id) {
        return mapper.logs(require(id));
    }

    public List<Summary> group(String comparisonGroup) {
        return executions.findByComparisonGroupOrderByIdAsc(comparisonGroup).stream().map(mapper::summary).toList();
    }

    public Timeline timeline(Long id) {
        Execution execution = require(id);
        List<StepView> steps = execution.getSteps().stream().map(mapper::step).toList();
        return new Timeline(execution.getId(), execution.getStartedAt(), execution.getDurationMs(),
                stages(execution.getSteps()), steps,
                "Todas as etapas e durações vêm de eventos reais de instrumentação. Etapas não registradas "
                        + "indicam que a camada não foi alcançada nesta execução.");
    }

    public Execution require(Long id) {
        return executions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EXECUTION_NOT_FOUND",
                        "Execução " + id + " não encontrada."));
    }

    @Transactional
    public void delete(Long id) {
        executions.delete(require(id));
        log.info("Execução {} excluída manualmente", id);
    }

    @Transactional
    public int purgeOlderThan(int days) {
        if (days < 1) {
            throw new InvalidInputException("INVALID_RETENTION", "A retenção mínima é de 1 dia.");
        }
        int removed = executions.deleteStartedBefore(Instant.now().minus(days, ChronoUnit.DAYS));
        log.info("{} execução(ões) com mais de {} dia(s) removida(s)", removed, days);
        return removed;
    }

    static List<Stage> stages(List<ExecutionStep> steps) {
        Map<StepLayer, List<ExecutionStep>> byLayer = new EnumMap<>(StepLayer.class);
        for (ExecutionStep step : steps) {
            byLayer.computeIfAbsent(step.getLayer(), k -> new ArrayList<>()).add(step);
        }
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("ORIGIN", "Angular (interface)", false, true, "INFO", null, 0,
                "Sem instrumentação no navegador: a execução foi disparada pela interface via "
                        + "POST /api/v1/scenarios/{código}/execute. Não existem dados de duração para esta etapa."));
        for (StepLayer layer : StepLayer.values()) {
            List<ExecutionStep> layerSteps = byLayer.getOrDefault(layer, List.of());
            if (layerSteps.isEmpty()) {
                stages.add(new Stage(layer.name(), layer.label(), true, false, "NOT_REACHED", null, 0,
                        "Nenhum evento registrado nesta camada: etapa não alcançada ou não aplicável."));
                continue;
            }
            int minDepth = layerSteps.stream().mapToInt(ExecutionStep::getDepth).min().orElse(0);
            List<Long> durations = layerSteps.stream().filter(s -> s.getDepth() == minDepth)
                    .map(ExecutionStep::getDurationMicros).filter(d -> d != null).toList();
            Long duration = durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).sum();
            String status = layerSteps.stream().map(ExecutionStep::getStatus)
                    .max(Comparator.comparingInt(ExecutionQueryService::severity)).map(StepStatus::name).orElse("OK");
            String note = duration == null ? "Eventos pontuais: não há duração medida para esta camada." : null;
            stages.add(new Stage(layer.name(), layer.label(), true, true, status, duration, layerSteps.size(), note));
        }
        return stages;
    }

    private static int severity(StepStatus status) {
        return switch (status) {
            case ERROR -> 5;
            case REJECTED -> 4;
            case WARN -> 3;
            case OK -> 2;
            case INFO -> 1;
        };
    }

    private static Specification<Execution> specification(ExecutionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startedAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("startedAt"), f.to()));
            }
            if (f.outcome() != null && !f.outcome().isBlank()) {
                predicates.add(cb.equal(root.get("outcome"),
                        ExecutionOutcome.valueOf(f.outcome().toUpperCase(Locale.ROOT))));
            }
            if (f.httpStatus() != null) {
                predicates.add(cb.equal(root.get("httpStatus"), f.httpStatus()));
            }
            if (f.scenario() != null && !f.scenario().isBlank()) {
                predicates.add(cb.equal(root.get("scenarioCode"), f.scenario().toUpperCase(Locale.ROOT)));
            }
            if (f.correlationId() != null && !f.correlationId().isBlank()) {
                predicates.add(cb.equal(root.get("correlationId"), f.correlationId().trim()));
            }
            if (f.comparisonGroup() != null && !f.comparisonGroup().isBlank()) {
                predicates.add(cb.equal(root.get("comparisonGroup"), f.comparisonGroup()));
            }
            if (f.exception() != null && !f.exception().isBlank()) {
                String like = "%" + f.exception().toLowerCase(Locale.ROOT).trim() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.<String>get("exceptionClass")), like),
                        cb.like(cb.lower(root.<String>get("rootCauseClass")), like)));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String term = f.q().trim();
                List<Predicate> any = new ArrayList<>();
                String numeric = term.startsWith("#") ? term.substring(1) : term;
                if (numeric.matches("\\d{1,18}")) {
                    any.add(cb.equal(root.get("id"), Long.parseLong(numeric)));
                }
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                any.add(cb.like(cb.lower(root.<String>get("correlationId")), like));
                any.add(cb.like(cb.lower(root.<String>get("traceId")), like));
                any.add(cb.like(cb.lower(root.<String>get("exceptionClass")), like));
                any.add(cb.like(cb.lower(root.<String>get("rootCauseClass")), like));
                any.add(cb.like(cb.lower(root.<String>get("scenarioCode")), like));
                any.add(cb.like(cb.lower(root.<String>get("errorCode")), like));
                predicates.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
