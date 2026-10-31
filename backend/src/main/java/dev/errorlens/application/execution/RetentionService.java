package dev.errorlens.application.execution;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.infrastructure.persistence.lab.CustomerRepository;
import dev.errorlens.infrastructure.persistence.lab.OrderRepository;

/**
 * Política de retenção: remove execuções antigas e dados descartáveis criados pelos cenários.
 * Dados semeados (origin=SEED) nunca são removidos.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final Duration DISPOSABLE_TTL = Duration.ofHours(1);

    private final ExecutionQueryService executions;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final ErrorLensProperties properties;

    public RetentionService(ExecutionQueryService executions, CustomerRepository customers, OrderRepository orders,
                            ErrorLensProperties properties) {
        this.executions = executions;
        this.customers = customers;
        this.orders = orders;
        this.properties = properties;
    }

    @Scheduled(cron = "${errorlens.retention.cron:0 15 3 * * *}")
    public void applyRetention() {
        int removed = executions.purgeOlderThan(properties.retention().days());
        log.info("Retenção aplicada: {} execução(ões) removida(s) (limite de {} dias)", removed,
                properties.retention().days());
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 120_000)
    @Transactional
    public LabCleanup cleanDisposableData() {
        Instant limit = Instant.now().minus(DISPOSABLE_TTL);
        int removedOrders = orders.deleteScenarioOrdersCreatedBefore(limit);
        int removedCustomers = customers.deleteDisposableCreatedBefore(limit);
        if (removedOrders + removedCustomers > 0) {
            log.info("Limpeza do laboratório: {} cliente(s) e {} pedido(s) descartáveis removidos", removedCustomers,
                    removedOrders);
        }
        return new LabCleanup(removedCustomers, removedOrders);
    }

    public int retentionDays() {
        return properties.retention().days();
    }

    public record LabCleanup(int customers, int orders) {
    }
}
