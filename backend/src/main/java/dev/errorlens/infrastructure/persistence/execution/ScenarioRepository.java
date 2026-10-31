package dev.errorlens.infrastructure.persistence.execution;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.errorlens.domain.scenario.Scenario;

public interface ScenarioRepository extends JpaRepository<Scenario, String> {

    List<Scenario> findByEnabledTrueOrderByDisplayOrderAsc();
}
