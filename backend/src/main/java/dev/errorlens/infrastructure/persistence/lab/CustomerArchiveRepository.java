package dev.errorlens.infrastructure.persistence.lab;

import java.time.Instant;
import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repositório de arquivamento de clientes. Grava na tabela {@code customer_archive}, que
 * propositalmente não existe no esquema (nenhuma migration a cria) para reproduzir, de forma
 * isolada, uma falha real de persistência (cenário D03).
 */
@Repository
public class CustomerArchiveRepository implements LabRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerArchiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int archive(Long customerId, String reason) {
        return jdbcTemplate.update(
                "INSERT INTO customer_archive (customer_id, reason, archived_at) VALUES (?, ?, ?)",
                customerId, reason, Timestamp.from(Instant.now()));
    }
}
