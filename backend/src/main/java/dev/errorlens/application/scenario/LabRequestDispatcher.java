package dev.errorlens.application.scenario;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.errorlens.config.ErrorLensProperties;

/**
 * Envia a requisição real do cenário ao próprio backend (loopback), atravessando Tomcat,
 * Spring Security, controllers, services, repositórios e banco. O RestClient é observado,
 * então o contexto de tracing (traceparent) é propagado.
 */
@Component
public class LabRequestDispatcher implements ApplicationListener<WebServerInitializedEvent> {

    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final RestClient.Builder builder;
    private final ErrorLensProperties properties;
    private volatile RestClient restClient;
    private volatile int port;

    public LabRequestDispatcher(RestClient.Builder builder, ErrorLensProperties properties) {
        this.builder = builder;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    public DispatchResult dispatch(ScenarioPlan plan, Map<String, String> headers) {
        RestClient.RequestBodySpec spec = client().method(HttpMethod.valueOf(plan.method())).uri(plan.path());
        headers.forEach(spec::header);
        if (plan.rawBody() != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(plan.rawBody());
        }
        return spec.exchange((request, response) -> {
            Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
            response.getHeaders().forEach((name, values) -> responseHeaders.put(name, List.copyOf(values)));
            return new DispatchResult(response.getStatusCode().value(), responseHeaders, read(response.getBody()));
        }, true);
    }

    public String baseUrl() {
        String configured = properties.runner().baseUrl();
        return configured == null || configured.isBlank() ? "http://127.0.0.1:" + port : configured;
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current == null) {
            synchronized (this) {
                if (restClient == null) {
                    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1).build());
                    factory.setReadTimeout(properties.runner().requestTimeout());
                    restClient = builder.clone().baseUrl(baseUrl()).requestFactory(factory).build();
                }
                current = restClient;
            }
        }
        return current;
    }

    private static String read(InputStream body) throws IOException {
        if (body == null) {
            return null;
        }
        byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES);
        return bytes.length == 0 ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public record DispatchResult(int status, Map<String, List<String>> headers, String body) {

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .findFirst()
                    .map(e -> e.getValue().isEmpty() ? null : e.getValue().getFirst())
                    .orElse(null);
        }
    }
}
