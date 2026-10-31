package dev.errorlens.interfaces.rest;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.execution.ExecutionQueryService;
import dev.errorlens.application.execution.ExecutionViews;
import dev.errorlens.application.scenario.ScenarioCatalogService;
import dev.errorlens.application.scenario.ScenarioRunner;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(name = "Scenario Lab", description = "Catálogo e execução real de cenários")
public class ScenarioController {

    private final ScenarioCatalogService catalog;
    private final ScenarioRunner runner;
    private final ExecutionQueryService executions;

    public ScenarioController(ScenarioCatalogService catalog, ScenarioRunner runner,
                              ExecutionQueryService executions) {
        this.catalog = catalog;
        this.runner = runner;
        this.executions = executions;
    }

    @GetMapping
    @Operation(summary = "Lista os cenários com estatísticas de uso")
    public List<ScenarioCatalogService.ScenarioView> list() {
        return catalog.list();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Detalhes de um cenário")
    public ScenarioCatalogService.ScenarioView get(@PathVariable String code) {
        return catalog.get(code);
    }

    @PostMapping("/{code}/execute")
    @Operation(summary = "Executa o cenário contra o backend real e retorna a investigação")
    public ExecutionViews.Detail execute(@PathVariable String code,
                                         @RequestBody(required = false) ExecuteRequest request,
                                         Authentication authentication) {
        ExecutionMode mode = request == null || request.mode() == null ? ExecutionMode.DEFAULT : request.mode();
        Execution execution = runner.execute(code.toUpperCase(), mode, authentication.getName(), null);
        return executions.detail(execution.getId());
    }

    @PostMapping("/{code}/compare-modes")
    @Operation(summary = "Executa as variantes de sucesso e falha e compara os resultados")
    public ScenarioCatalogService.CompareModesResult compareModes(@PathVariable String code,
                                                                  Authentication authentication) {
        return catalog.compareModes(code.toUpperCase(), authentication.getName());
    }

    public record ExecuteRequest(ExecutionMode mode) {
    }
}
