package dev.errorlens.infrastructure.simulator;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import dev.errorlens.infrastructure.observability.ExecutionContextHolder;
import dev.errorlens.infrastructure.observability.MdcKeys;
import dev.errorlens.infrastructure.observability.StepRecord;

/**
 * Propaga o correlationId para o dependency-simulator e registra cada chamada HTTP de saída
 * como etapa "Chamada externa". O traceparent (W3C) é propagado pela observação do RestClient.
 */
public class OutboundCallInterceptor implements ClientHttpRequestInterceptor {

    private final String targetName;

    public OutboundCallInterceptor(String targetName) {
        this.targetName = targetName;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        if (correlationId != null) {
            request.getHeaders().set(MdcKeys.CORRELATION_HEADER, correlationId);
        }
        String target = request.getMethod() + " " + request.getURI().getPath();
        StepRecord step = ExecutionContextHolder.openStep(StepLayer.EXTERNAL, targetName + " · " + target,
                "RestClient", null);
        try {
            ClientHttpResponse response = execution.execute(request, body);
            int status = response.getStatusCode().value();
            ExecutionContextHolder.closeStep(step, status >= 500 ? StepStatus.ERROR
                            : status >= 400 ? StepStatus.WARN : StepStatus.OK,
                    target + " -> HTTP " + status, null);
            return response;
        } catch (IOException | RuntimeException ex) {
            ExecutionContextHolder.closeStep(step, StepStatus.ERROR, target + " -> falha de comunicação", ex);
            throw ex;
        }
    }
}
