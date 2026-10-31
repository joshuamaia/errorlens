package dev.errorlens.application.lab;

import java.math.BigDecimal;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.domain.lab.CustomerOrigin;
import dev.errorlens.infrastructure.persistence.lab.CustomerRepository;
import dev.errorlens.infrastructure.persistence.lab.OrderRepository;

/**
 * Relatório de auditoria protegido por ROLE_AUDITOR (cenários C01-C03).
 */
@Service
public class AuditReportService {

    private static final Logger log = LoggerFactory.getLogger(AuditReportService.class);

    private final CustomerRepository customers;
    private final OrderRepository orders;

    public AuditReportService(CustomerRepository customers, OrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public AuditReport generate(String requestedBy) {
        log.info("Gerando relatório de auditoria solicitado por {}", requestedBy);
        return new AuditReport(Instant.now(), requestedBy, customers.count(),
                customers.countByOrigin(CustomerOrigin.SEED), orders.count(), orders.totalAmount());
    }

    public record AuditReport(Instant generatedAt, String requestedBy, long customers, long seedCustomers,
                              long orders, BigDecimal ordersTotalAmount) {
    }
}
