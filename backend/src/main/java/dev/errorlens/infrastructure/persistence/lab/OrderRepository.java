package dev.errorlens.infrastructure.persistence.lab;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.errorlens.domain.lab.Order;

public interface OrderRepository extends JpaRepository<Order, Long>, LabRepository {

    long countByCustomerId(Long customerId);

    long countByDescription(String description);

    @Query("select coalesce(sum(o.amount), 0) from Order o")
    BigDecimal totalAmount();

    /**
     * Remove pedidos criados por cenários (checkout bem-sucedido) de clientes descartáveis.
     */
    @Modifying
    @Query(value = """
            DELETE FROM orders o
            USING customers c
            WHERE o.customer_id = c.id AND c.origin = 'SCENARIO' AND o.created_at < :limit
            """, nativeQuery = true)
    int deleteScenarioOrdersCreatedBefore(@Param("limit") Instant limit);
}
