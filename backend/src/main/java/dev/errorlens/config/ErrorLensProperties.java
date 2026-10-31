package dev.errorlens.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configurações tipadas do ErrorLens (prefixo {@code errorlens}).
 */
@ConfigurationProperties(prefix = "errorlens")
public record ErrorLensProperties(
        @DefaultValue Lab lab,
        @DefaultValue Runner runner,
        @DefaultValue Security security,
        @DefaultValue Simulator simulator,
        @DefaultValue Retention retention,
        @DefaultValue Health health,
        @DefaultValue Analyzer analyzer) {

    public record Lab(@DefaultValue("true") boolean enabled) {
    }

    public record Runner(
            @DefaultValue("") String baseUrl,
            @DefaultValue("20s") Duration requestTimeout,
            @DefaultValue("4") int maxConcurrentExecutions) {
    }

    public record Security(
            @DefaultValue("8h") Duration tokenTtl,
            @DefaultValue Users users) {
    }

    public record Users(
            @DefaultValue("errorlens") String adminPassword,
            @DefaultValue("analyst-lab") String analystPassword,
            @DefaultValue("viewer-lab") String viewerPassword,
            @DefaultValue("auditor-lab") String auditorPassword,
            @DefaultValue("runner-lab") String runnerPassword) {
    }

    public record Simulator(
            @DefaultValue("http://localhost:8090") String baseUrl,
            @DefaultValue("lab-control-token") String controlToken,
            @DefaultValue("1s") Duration connectTimeout,
            @DefaultValue("6s") Duration readTimeout) {
    }

    public record Retention(
            @DefaultValue("30") int days,
            @DefaultValue("0 15 3 * * *") String cron) {
    }

    public record Health(
            @DefaultValue("http://localhost:13133/") String otelCollectorUrl,
            @DefaultValue("http://localhost:16686/") String jaegerUrl,
            @DefaultValue("http://localhost:16686") String jaegerPublicUrl) {
    }

    public record Analyzer(
            @DefaultValue("524288") int maxStacktraceChars,
            @DefaultValue("2097152") long maxLogBytes,
            @DefaultValue("5000") int maxLogEntries) {
    }
}
