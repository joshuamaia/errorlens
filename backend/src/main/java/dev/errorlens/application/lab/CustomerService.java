package dev.errorlens.application.lab;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.lab.Customer;
import dev.errorlens.domain.lab.CustomerOrigin;
import dev.errorlens.domain.lab.CustomerStatus;
import dev.errorlens.infrastructure.persistence.lab.CustomerRepository;

/**
 * Operações de clientes do laboratório. Não há pré-verificações de unicidade ou de pedidos
 * associados: as constraints do PostgreSQL são a fonte de verdade (cenários D01/D02).
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public Customer findById(Long id) {
        log.info("Consultando cliente id={}", id);
        return customers.findById(id).orElseThrow(() -> {
            log.warn("Cliente id={} não encontrado", id);
            return ResourceNotFoundException.customer(id);
        });
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(Pageable pageable) {
        log.info("Listando clientes: página={}, tamanho={}, ordenação={}", pageable.getPageNumber(),
                pageable.getPageSize(), pageable.getSort());
        return customers.findAll(pageable);
    }

    @Transactional
    public Customer create(String name, String email, String document, BigDecimal creditLimit) {
        log.info("Criando cliente '{}'", name);
        Customer saved = customers.saveAndFlush(
                new Customer(name, email, document, creditLimit, CustomerOrigin.SCENARIO));
        log.info("Cliente criado com id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Customer update(Long id, String name, CustomerStatus status, BigDecimal creditLimit) {
        Customer customer = findById(id);
        log.info("Atualizando cliente id={} (versão atual {})", id, customer.getVersion());
        customer.update(name, status, creditLimit);
        Customer saved = customers.saveAndFlush(customer);
        log.info("Cliente id={} atualizado para a versão {}", id, saved.getVersion());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findById(id);
        log.info("Excluindo cliente id={}", id);
        customers.delete(customer);
        customers.flush();
        log.info("Cliente id={} excluído", id);
    }
}
