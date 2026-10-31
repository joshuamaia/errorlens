package dev.errorlens.infrastructure.persistence.lab;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.errorlens.domain.lab.Customer;
import dev.errorlens.domain.lab.CustomerOrigin;

public interface CustomerRepository extends JpaRepository<Customer, Long>, LabRepository {

    Optional<Customer> findByEmail(String email);

    long countByOrigin(CustomerOrigin origin);

    /**
     * Remove clientes descartáveis criados por cenários (sem pedidos) mais antigos que o limite.
     */
    @Modifying
    @Query(value = """
            DELETE FROM customers c
            WHERE c.origin = 'SCENARIO' AND c.created_at < :limit
              AND NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id)
            """, nativeQuery = true)
    int deleteDisposableCreatedBefore(@Param("limit") Instant limit);
}
