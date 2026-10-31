package dev.errorlens.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Implementação mínima, em processo, do contrato HTTP do dependency-simulator para os testes de
 * integração (mesma semântica de modos e contadores por chave de simulação).
 */
public final class FakeDependencySimulator implements AutoCloseable {

    private static final Pattern MODE = Pattern.compile("\"mode\"\\s*:\\s*\"(\\w+)\"");
    private static final Pattern DELAY = Pattern.compile("\"delayMs\"\\s*:\\s*(\\d+)");
    private static final Pattern FAILURES = Pattern.compile("\"failures\"\\s*:\\s*(\\d+)");

    private final HttpServer server;
    private final Map<String, Config> behaviors = new ConcurrentHashMap<>();

    public FakeDependencySimulator() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/internal/control", this::control);
        server.createContext("/api/v1", this::business);
        server.createContext("/actuator/health", ex -> send(ex, 200, "{\"status\":\"UP\"}"));
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public int behaviorCount() {
        return behaviors.size();
    }

    private void control(HttpExchange exchange) throws IOException {
        if (!"lab-control-token".equals(exchange.getRequestHeaders().getFirst("X-Lab-Control-Token"))) {
            send(exchange, 403, "{}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String key = path.substring(path.lastIndexOf('/') + 1);
        switch (exchange.getRequestMethod()) {
            case "PUT" -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                behaviors.put(key, new Config(group(MODE, body, "IMMEDIATE"),
                        Integer.parseInt(group(DELAY, body, "0")), Integer.parseInt(group(FAILURES, body, "0")),
                        new AtomicInteger()));
                send(exchange, 200, "{}");
            }
            case "DELETE" -> {
                behaviors.remove(key);
                send(exchange, 204, null);
            }
            default -> {
                behaviors.clear();
                send(exchange, 200, "{}");
            }
        }
    }

    private void business(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        Config config = behaviors.get(String.valueOf(exchange.getRequestHeaders().getFirst("X-Simulation-Key")));
        String mode = config == null ? "IMMEDIATE" : config.mode();
        int call = config == null ? 1 : config.calls().incrementAndGet();
        if (config != null && config.delayMs() > 0) {
            try {
                Thread.sleep(config.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int status = switch (mode) {
            case "ERROR_500" -> 500;
            case "ERROR_503", "UNAVAILABLE" -> 503;
            case "FAIL_FIRST_N" -> call <= config.failures() ? 503 : 200;
            default -> 200;
        };
        send(exchange, status, status == 200
                ? "{\"ok\":true,\"call\":" + call + ",\"items\":[{\"sku\":\"X\"}],\"score\":700}"
                : "{\"error\":\"simulated\",\"call\":" + call + "}");
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        try (exchange) {
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // cliente pode ter cancelado a chamada (timeout)
        }
    }

    private static String group(Pattern pattern, String body, String fallback) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : fallback;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record Config(String mode, int delayMs, int failures, AtomicInteger calls) {
    }
}
