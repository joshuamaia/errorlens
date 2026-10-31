package dev.errorlens.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.diagnosis.rules.DeclarativeRuleLoader;
import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.exception.InvalidInputException;
import tools.jackson.databind.json.JsonMapper;

class AnalyzersTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private ErrorLensProperties properties;
    private HttpErrorAnalyzer httpAnalyzer;
    private LogAnalyzer logAnalyzer;

    @BeforeEach
    void setUp() {
        properties = new ErrorLensProperties(null, null, null, null, null, null,
                new ErrorLensProperties.Analyzer(524_288, 2_097_152, 5000));
        DiagnosisEngine engine = new DiagnosisEngine(List.of(), new DeclarativeRuleLoader(mapper));
        httpAnalyzer = new HttpErrorAnalyzer(mapper, engine);
        logAnalyzer = new LogAnalyzer(mapper, properties);
    }

    @Test
    void interpretsProblemDetailsWithValidationErrors() {
        var analysis = httpAnalyzer.analyze(new HttpErrorAnalyzer.HttpErrorInput("POST", "http://x/api/v1/customers",
                400, "Content-Type: application/problem+json", """
                {"type":"about:blank","title":"Bad Request","status":400,"detail":"inválido",
                 "errorCode":"VALIDATION_FAILED","errors":[{"field":"email","message":"inválido"}]}
                """));
        assertThat(analysis.format()).isEqualTo("PROBLEM_DETAILS");
        assertThat(analysis.extracted()).containsEntry("errorCode", "VALIDATION_FAILED");
        assertThat(analysis.findings()).anyMatch(f -> f.title().contains("validação"));
        assertThat(analysis.diagnoses().getFirst().ruleId()).isEqualTo("BEAN_VALIDATION");
    }

    @Test
    void detectsExposedStackTraceAndStatusMismatch() {
        var analysis = httpAnalyzer.analyze(new HttpErrorAnalyzer.HttpErrorInput("GET", "http://x/a", 500,
                "Content-Type: application/json", """
                {"timestamp":"2026-01-01T00:00:00Z","status":503,"error":"x","path":"/a",
                 "exception":"java.lang.NullPointerException","trace":"java.lang.NullPointerException\\n\\tat a.B.c(B.java:1)"}
                """));
        assertThat(analysis.format()).isEqualTo("SPRING_BOOT_ERROR");
        assertThat(analysis.findings()).extracting(HttpErrorAnalyzer.Finding::title)
                .contains("Stack trace exposto na resposta", "Status do corpo diferente do status HTTP");
        assertThat(analysis.diagnoses()).anyMatch(d -> d.ruleId().equals("NULL_REFERENCE"));
    }

    @Test
    void reportsAllowHeaderOn405() {
        var analysis = httpAnalyzer.analyze(new HttpErrorAnalyzer.HttpErrorInput("PATCH", "http://x/c", 405,
                "Allow: GET, POST", ""));
        assertThat(analysis.format()).isEqualTo("EMPTY");
        assertThat(analysis.findings().getFirst().evidence()).isEqualTo("Allow: GET, POST");
    }

    @Test
    void groupsStructuredLogsByCorrelationId() {
        String logs = """
                {"@timestamp":"2026-10-31T12:00:00.100Z","level":"INFO","logger_name":"a.B","message":"start","correlationId":"aaaaaaaa-1"}
                {"@timestamp":"2026-10-31T12:00:00.300Z","level":"ERROR","logger_name":"a.B","message":"falhou","correlationId":"aaaaaaaa-1","stack_trace":"java.lang.IllegalStateException: x\\n\\tat a.B.c(B.java:1)"}
                {"@timestamp":"2026-10-31T12:00:01.000Z","level":"WARN","logger_name":"a.C","message":"lento","correlationId":"bbbbbbbb-2"}
                """;
        var analysis = logAnalyzer.analyze(logs, "teste");
        assertThat(analysis.format()).isEqualTo("JSON");
        assertThat(analysis.levelCounts()).containsEntry("ERROR", 1L).containsEntry("WARN", 1L);
        assertThat(analysis.groups().getFirst().correlationId()).isEqualTo("aaaaaaaa-1");
        assertThat(analysis.groups().getFirst().durationMs()).isEqualTo(200L);
        assertThat(analysis.exceptions().getFirst().className()).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void parsesTextLogsWithMultilineStackTraces() {
        String logs = """
                2026-10-31 14:22:11.100 INFO  [main] a.b.Service - iniciando correlationId=cid-12345678
                2026-10-31 14:22:12.598 ERROR [main] a.b.Handler - erro correlationId=cid-12345678
                dev.x.SomeException: boom
                \tat a.b.C.d(C.java:1)
                """;
        var analysis = logAnalyzer.analyze(logs, "texto");
        assertThat(analysis.totalEntries()).isEqualTo(2);
        assertThat(analysis.entries().get(1).details()).contains("SomeException");
        assertThat(analysis.entries().get(1).exceptionClass()).isEqualTo("dev.x.SomeException");
        assertThat(analysis.groups()).hasSize(1);
    }

    @Test
    void rejectsUnsupportedOrBinaryFiles() {
        assertThatThrownBy(() -> logAnalyzer.analyzeFile("x.exe", new byte[] {1}))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> logAnalyzer.analyzeFile("x.log", new byte[] {(byte) 0xC3, (byte) 0x28}))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> logAnalyzer.analyzeFile("x.log", "a\0b".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidInputException.class);
    }
}
