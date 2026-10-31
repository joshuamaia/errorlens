package dev.errorlens.infrastructure.persistence.execution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.errorlens.domain.execution.Execution;

public interface ExecutionRepository extends JpaRepository<Execution, Long>, JpaSpecificationExecutor<Execution> {

    Optional<Execution> findByCorrelationId(String correlationId);

    List<Execution> findByComparisonGroupOrderByIdAsc(String comparisonGroup);

    @Query("select e.id from Execution e where e.startedAt < :limit")
    List<Long> findIdsStartedBefore(@Param("limit") Instant limit);

    @Modifying
    @Query(value = "DELETE FROM executions WHERE started_at < :limit", nativeQuery = true)
    int deleteStartedBefore(@Param("limit") Instant limit);
}
