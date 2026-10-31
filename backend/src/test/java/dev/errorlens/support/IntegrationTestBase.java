package dev.errorlens.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Base dos testes de integração: PostgreSQL real (Testcontainers), servidor HTTP real em porta
 * aleatória e simulador de dependências em processo. O container é um singleton compartilhado por
 * todas as classes de teste (o contexto Spring em cache continua válido entre classes).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.tracing.export.enabled=false",
        "logging.structured.format.console="
})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    protected static final FakeDependencySimulator SIMULATOR;

    static {
        POSTGRES.start();
        try {
            SIMULATOR = new FakeDependencySimulator();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("errorlens.simulator.base-url", SIMULATOR::baseUrl);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JsonMapper jsonMapper;

    protected RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    protected String login(String username, String password) {
        Map<String, Object> body = client().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", password)).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        return "Bearer " + body.get("token");
    }

    protected String adminToken() {
        return login("admin", "errorlens");
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> execute(String code, String mode) {
        return client().post().uri("/api/v1/scenarios/{code}/execute", code)
                .header(HttpHeaders.AUTHORIZATION, adminToken()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("mode", mode)).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> summary(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("summary");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
