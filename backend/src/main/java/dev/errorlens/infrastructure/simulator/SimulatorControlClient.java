package dev.errorlens.infrastructure.simulator;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.errorlens.config.ErrorLensProperties;

/**
 * Cliente dos endpoints de controle do simulador (somente perfil lab), protegidos por token
 * compartilhado. Usado pelo Scenario Runner para configurar comportamentos por execução.
 */
@Component
public class SimulatorControlClient {

    private static final String TOKEN_HEADER = "X-Lab-Control-Token";

    private final RestClient restClient;
    private final String token;

    public SimulatorControlClient(RestClient.Builder builder, ErrorLensProperties properties) {
        this.token = properties.simulator().controlToken();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.simulator().connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder.clone().baseUrl(properties.simulator().baseUrl()).requestFactory(factory).build();
    }

    public void configure(String key, SimulatorBehavior behavior) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", behavior.mode().name());
        body.put("delayMs", behavior.delayMs());
        body.put("failures", behavior.failures());
        body.put("status", behavior.status());
        restClient.put()
                .uri("/internal/control/behaviors/{key}", key)
                .header(TOKEN_HEADER, token)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void remove(String key) {
        restClient.delete()
                .uri("/internal/control/behaviors/{key}", key)
                .header(TOKEN_HEADER, token)
                .retrieve()
                .toBodilessEntity();
    }

    public void resetAll() {
        restClient.post()
                .uri("/internal/control/reset")
                .header(TOKEN_HEADER, token)
                .retrieve()
                .toBodilessEntity();
    }
}
