package dev.errorlens.application.health;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import dev.errorlens.config.ErrorLensProperties;

/**
 * Verifica a saúde dos serviços do laboratório em paralelo. As URLs são fixas na configuração
 * (rede interna do Compose); nenhuma URL arbitrária é consultada. O resultado é cacheado por 5s.
 */
@Service
public class ServiceHealthService {

    private static final Duration TIMEOUT = Duration.ofMillis(1500);
    private static final Duration CACHE_TTL = Duration.ofSeconds(5);

    private final JdbcTemplate jdbc;
    private final ErrorLensProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1).build();
    private volatile HealthReport cached;

    public ServiceHealthService(JdbcTemplate jdbc, ErrorLensProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public HealthReport check() {
        HealthReport current = cached;
        if (current != null && current.checkedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return current;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ServiceStatus>> futures = List.of(
                    CompletableFuture.supplyAsync(this::backend, executor),
                    CompletableFuture.supplyAsync(this::postgres, executor),
                    CompletableFuture.supplyAsync(() -> http("dependency-simulator", "Dependency Simulator",
                            properties.simulator().baseUrl() + "/actuator/health", null), executor),
                    CompletableFuture.supplyAsync(() -> http("otel-collector", "OpenTelemetry Collector",
                            properties.health().otelCollectorUrl(), null), executor),
                    CompletableFuture.supplyAsync(() -> http("jaeger", "Jaeger (Tracing)",
                            properties.health().jaegerUrl(), properties.health().jaegerPublicUrl()), executor));
            List<ServiceStatus> services = futures.stream().map(CompletableFuture::join).toList();
            String overall = services.stream().allMatch(s -> "UP".equals(s.status())) ? "UP"
                    : services.stream().anyMatch(s -> "UP".equals(s.status())) ? "DEGRADED" : "DOWN";
            HealthReport report = new HealthReport(overall, services, Instant.now(),
                    properties.health().jaegerPublicUrl());
            cached = report;
            return report;
        }
    }

    private ServiceStatus backend() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new ServiceStatus("backend", "Backend (Spring Boot)", "UP", 0L,
                "Java " + Runtime.version().feature() + " • uptime " + uptime + " s", null);
    }

    private ServiceStatus postgres() {
        return timed("postgres", "PostgreSQL", () -> {
            String version = jdbc.queryForObject("SHOW server_version", String.class);
            return "PostgreSQL " + version;
        });
    }

    private ServiceStatus http(String key, String name, String url, String publicUrl) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = (System.nanoTime() - start) / 1_000_000;
            boolean up = response.statusCode() < 400;
            return new ServiceStatus(key, name, up ? "UP" : "DOWN", latency,
                    "HTTP " + response.statusCode(), publicUrl);
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ServiceStatus(key, name, "DOWN", null, "Indisponível: " + ex.getClass().getSimpleName(),
                    publicUrl);
        }
    }

    private static ServiceStatus timed(String key, String name, Supplier<String> probe) {
        long start = System.nanoTime();
        try {
            String detail = probe.get();
            return new ServiceStatus(key, name, "UP", (System.nanoTime() - start) / 1_000_000, detail, null);
        } catch (RuntimeException ex) {
            return new ServiceStatus(key, name, "DOWN", null, "Indisponível: " + ex.getClass().getSimpleName(), null);
        }
    }

    public record ServiceStatus(String key, String name, String status, Long latencyMs, String detail,
                                String publicUrl) {
    }

    public record HealthReport(String overall, List<ServiceStatus> services, Instant checkedAt,
                               String jaegerPublicUrl) {
    }
}
