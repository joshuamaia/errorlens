package dev.errorlens.interfaces.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.execution.ComparisonService;
import dev.errorlens.application.execution.ExecutionFilter;
import dev.errorlens.application.execution.ExecutionQueryService;
import dev.errorlens.application.execution.ExecutionViews;
import dev.errorlens.application.report.ReportService;
import dev.errorlens.interfaces.lab.dto.LabDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints de diagnóstico (ROLE_ANALYST/ADMIN): histórico, investigação, timeline, logs,
 * comparação e exportação de relatórios.
 */
@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "Execuções", description = "Histórico, investigação, timeline, comparação e relatórios")
public class ExecutionController {

    private final ExecutionQueryService queries;
    private final ComparisonService comparison;
    private final ReportService reports;

    public ExecutionController(ExecutionQueryService queries, ComparisonService comparison, ReportService reports) {
        this.queries = queries;
        this.comparison = comparison;
        this.reports = reports;
    }

    @GetMapping
    @Operation(summary = "Histórico paginado com filtros")
    public PageResponse<ExecutionViews.Summary> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) Integer httpStatus,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String exception,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        ExecutionFilter filter = new ExecutionFilter(from, to, outcome, httpStatus, scenario, correlationId, exception,
                q, group);
        return PageResponse.from(queries.search(filter, page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Investigação completa de uma execução")
    public ExecutionViews.Detail detail(@PathVariable Long id) {
        return queries.detail(id);
    }

    @GetMapping("/by-correlation/{correlationId}")
    @Operation(summary = "Localiza uma execução pelo correlationId")
    public ExecutionViews.Detail byCorrelation(@PathVariable String correlationId) {
        return queries.detailByCorrelation(correlationId);
    }

    @GetMapping("/{id}/logs")
    @Operation(summary = "Logs correlacionados da execução")
    public List<ExecutionViews.LogView> logs(@PathVariable Long id) {
        return queries.logs(id);
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Timeline baseada em eventos reais de instrumentação")
    public ExecutionViews.Timeline timeline(@PathVariable Long id) {
        return queries.timeline(id);
    }

    @GetMapping("/{id}/report")
    @Operation(summary = "Exporta relatório (json, markdown ou pdf)")
    public ResponseEntity<byte[]> report(@PathVariable Long id, @RequestParam(defaultValue = "json") String format) {
        ReportService.ReportFile file = reports.export(id, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, file.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }

    @PostMapping("/compare")
    @Operation(summary = "Compara duas execuções")
    public ComparisonService.Comparison compare(@Valid @RequestBody CompareRequest request) {
        return comparison.compare(request.leftId(), request.rightId());
    }

    @GetMapping("/groups/{group}")
    @Operation(summary = "Execuções de um mesmo grupo de comparação")
    public List<ExecutionViews.Summary> group(@PathVariable String group) {
        return queries.group(group);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui uma execução (ROLE_ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        queries.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Remove execuções mais antigas que N dias (ROLE_ADMIN)")
    public Map<String, Object> purge(@RequestParam int olderThanDays) {
        return Map.of("removed", queries.purgeOlderThan(olderThanDays), "olderThanDays", olderThanDays);
    }

    public record CompareRequest(@NotNull Long leftId, @NotNull Long rightId) {
    }
}
