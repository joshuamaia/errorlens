package dev.errorlens.application.scenario;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.errorlens.domain.exception.ResourceNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tools.jackson.databind.json.JsonMapper;

/**
 * Utilitários usados pelos handlers para preparar dados determinísticos e isolados e para
 * verificar o estado do banco após a execução. Atua somente sobre dados de laboratório.
 */
@Component
public class ScenarioSupport {

    public static final String SEED_ANA = "ana.souza@errorlens.dev";
    public static final String SEED_BRUNO = "bruno.lima@errorlens.dev";
    public static final String SEED_CARLA = "carla.mendes@errorlens.dev";
    public static final long MISSING_ID = 999_999L;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final CircuitBreakerRegistry circuitBreakers;

    public ScenarioSupport(JdbcTemplate jdbc, JsonMapper jsonMapper, CircuitBreakerRegistry circuitBreakers) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.circuitBreakers = circuitBreakers;
    }

    public long seedCustomerId(String email) {
        Long id = jdbc.query("SELECT id FROM customers WHERE email = ? AND origin = 'SEED'",
                rs -> rs.next() ? rs.getLong(1) : null, email);
        if (id == null) {
            throw new ResourceNotFoundException("SEED_DATA_MISSING",
                    "Dado semeado ausente (" + email + "). Recrie o banco do laboratório.");
        }
        return id;
    }

    /**
     * Cria um cliente descartável (origin=SCENARIO) usado apenas pela execução atual.
     */
    public long createDisposableCustomer(String label, BigDecimal creditLimit) {
        return jdbc.queryForObject("""
                INSERT INTO customers (name, email, credit_limit, origin, loyalty_tier)
                VALUES (?, ?, ?, 'SCENARIO', 'BRONZE') RETURNING id
                """, Long.class, "Cliente descartável " + label, uniqueEmail("descartavel"), creditLimit);
    }

    public long orderIdWithMetadata(String metadata) {
        Long id = jdbc.query("SELECT min(id) FROM orders WHERE invoice_metadata = ?",
                rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, metadata);
        if (id == null) {
            throw new ResourceNotFoundException("SEED_DATA_MISSING", "Pedido semeado com metadado '" + metadata
                    + "' ausente.");
        }
        return id;
    }

    public String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 12) + "@lab.errorlens.dev";
    }

    public String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public void resetCircuit(String name) {
        circuitBreakers.circuitBreaker(name).reset();
    }
}
