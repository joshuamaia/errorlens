package dev.errorlens.application.lab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.domain.lab.Customer;
import dev.errorlens.infrastructure.persistence.lab.CustomerArchiveRepository;

/**
 * Arquivamento de clientes (cenário D03). Depende de uma tabela inexistente no esquema.
 */
@Service
public class PersistenceArchiveService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceArchiveService.class);

    private final CustomerService customerService;
    private final CustomerArchiveRepository archiveRepository;

    public PersistenceArchiveService(CustomerService customerService, CustomerArchiveRepository archiveRepository) {
        this.customerService = customerService;
        this.archiveRepository = archiveRepository;
    }

    @Transactional
    public void archive(Long customerId, String reason) {
        Customer customer = customerService.findById(customerId);
        log.info("Arquivando cliente id={} (motivo: {})", customer.getId(), reason);
        archiveRepository.archive(customer.getId(), reason);
    }
}
