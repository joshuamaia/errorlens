package dev.errorlens.interfaces.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.errorlens.application.analyzer.ExampleCatalog;
import dev.errorlens.application.analyzer.HttpErrorAnalyzer;
import dev.errorlens.application.analyzer.LogAnalyzer;
import dev.errorlens.application.analyzer.StackTraceAnalysisService;
import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.domain.exception.InvalidInputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/analyzer")
@Tag(name = "Analisadores", description = "Stack traces, erros HTTP e logs importados")
public class AnalyzerController {

    private final StackTraceAnalysisService stackTraces;
    private final HttpErrorAnalyzer httpErrors;
    private final LogAnalyzer logs;
    private final ExampleCatalog examples;
    private final DiagnosisEngine engine;

    public AnalyzerController(StackTraceAnalysisService stackTraces, HttpErrorAnalyzer httpErrors, LogAnalyzer logs,
                              ExampleCatalog examples, DiagnosisEngine engine) {
        this.stackTraces = stackTraces;
        this.httpErrors = httpErrors;
        this.logs = logs;
        this.examples = examples;
        this.engine = engine;
    }

    @PostMapping("/stacktrace")
    @Operation(summary = "Analisa um stack trace Java")
    public StackTraceAnalysisService.StackTraceAnalysis stackTrace(@RequestBody StackTraceRequest request) {
        return stackTraces.analyze(request.content(), request.applicationPackages());
    }

    @PostMapping("/http-error")
    @Operation(summary = "Interpreta uma resposta HTTP de erro")
    public HttpErrorAnalyzer.HttpErrorAnalysis httpError(@RequestBody HttpErrorAnalyzer.HttpErrorInput request) {
        return httpErrors.analyze(request);
    }

    @PostMapping(value = "/logs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analisa um arquivo de log (.log, .txt, .json)")
    public LogAnalyzer.LogAnalysis logFile(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new InvalidInputException("EMPTY_FILE", "O arquivo enviado está vazio.");
        }
        return logs.analyzeFile(file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping("/logs/text")
    @Operation(summary = "Analisa logs colados como texto")
    public LogAnalyzer.LogAnalysis logText(@Valid @RequestBody LogTextRequest request) {
        return logs.analyze(request.content(), request.source() == null ? "texto colado" : request.source());
    }

    @GetMapping("/examples")
    @Operation(summary = "Lista exemplos prontos")
    public List<ExampleCatalog.ExampleDescriptor> examples(@RequestParam(required = false) String type) {
        return examples.list(type);
    }

    @GetMapping("/examples/{id}")
    @Operation(summary = "Conteúdo de um exemplo")
    public ExampleCatalog.Example example(@PathVariable String id) {
        return examples.get(id);
    }

    @GetMapping("/rules")
    @Operation(summary = "Regras ativas do Diagnosis Engine")
    public Map<String, Object> rules() {
        return Map.of("count", engine.ruleCount(), "rules", engine.ruleIds());
    }

    public record StackTraceRequest(String content, List<String> applicationPackages) {
    }

    public record LogTextRequest(@NotBlank @Size(max = 2_097_152) String content, String source) {
    }
}
