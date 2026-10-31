package dev.errorlens.infrastructure.simulator;

import java.net.http.HttpClient;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.infrastructure.observability.MdcKeys;

/**
 * Cliente HTTP das APIs de negócio do dependency-simulator. A URL base é fixa (configuração),
 * nunca informada pelo usuário.
 */
@Component
public class DependencySimulatorClient {

    public static final String TARGET = "Dependency Simulator";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public DependencySimulatorClient(RestClient.Builder builder, ErrorLensProperties properties) {
        ErrorLensProperties.Simulator simulator = properties.simulator();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(simulator.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(simulator.readTimeout());
        this.restClient = builder.clone()
                .baseUrl(simulator.baseUrl())
                .requestFactory(factory)
                .requestInterceptor(new OutboundCallInterceptor(TARGET))
                .build();
    }

    public Map<String, Object> creditScore(long customerId, String simulationKey) {
        return get("/api/v1/credit-score/{id}", simulationKey, customerId);
    }

    public Map<String, Object> customerProfile(long customerId, String simulationKey) {
        return get("/api/v1/customer-profile/{id}", simulationKey, customerId);
    }

    public Map<String, Object> recommendations(long customerId, String simulationKey) {
        return get("/api/v1/recommendations/{id}", simulationKey, customerId);
    }

    public Map<String, Object> authorizePayment(String simulationKey, int sequence) {
        return restClient.post()
                .uri("/api/v1/payments/authorize")
                .headers(headers -> applyKey(headers, simulationKey))
                .body(Map.of("sequence", sequence, "amount", 199.90))
                .retrieve()
                .body(MAP);
    }

    private Map<String, Object> get(String path, String simulationKey, Object... vars) {
        return restClient.get()
                .uri(path, vars)
                .headers(headers -> applyKey(headers, simulationKey))
                .retrieve()
                .body(MAP);
    }

    private static void applyKey(org.springframework.http.HttpHeaders headers, String simulationKey) {
        if (simulationKey != null) {
            headers.set(MdcKeys.SIMULATION_KEY_HEADER, simulationKey);
        }
    }
}
