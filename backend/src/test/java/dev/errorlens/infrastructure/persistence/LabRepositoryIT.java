package dev.errorlens.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import dev.errorlens.domain.lab.Customer;
import dev.errorlens.domain.lab.CustomerOrigin;
import dev.errorlens.infrastructure.persistence.lab.CustomerRepository;
import dev.errorlens.infrastructure.persistence.lab.OrderRepository;

/**
 * Constraints reais do esquema Flyway (unique e foreign key) no PostgreSQL.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LabRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    CustomerRepository customers;

    @Autowired
    OrderRepository orders;

    @Test
    void seedDataIsAvailable() {
        assertThat(customers.countByOrigin(CustomerOrigin.SEED)).isEqualTo(12);
        Customer ana = customers.findByEmail("ana.souza@errorlens.dev").orElseThrow();
        assertThat(orders.countByCustomerId(ana.getId())).isEqualTo(2);
    }

    @Test
    void uniqueEmailIsEnforcedByDatabase() {
        assertThatThrownBy(() -> customers.saveAndFlush(new Customer("Duplicada", "ana.souza@errorlens.dev", null,
                BigDecimal.TEN, CustomerOrigin.SCENARIO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_customers_email");
    }

    @Test
    void foreignKeyProtectsCustomersWithOrders() {
        Customer ana = customers.findByEmail("ana.souza@errorlens.dev").orElseThrow();
        assertThatThrownBy(() -> {
            customers.delete(ana);
            customers.flush();
        }).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_orders_customer");
    }
}
