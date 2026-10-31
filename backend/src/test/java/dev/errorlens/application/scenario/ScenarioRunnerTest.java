package dev.errorlens.application.scenario;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.errorlens.application.execution.ExecutionPersistenceService;
import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.domain.scenario.Scenario;
import dev.errorlens.infrastructure.observability.ExecutionRecorder;
import dev.errorlens.infrastructure.persistence.execution.ScenarioRepository;
import dev.errorlens.infrastructure.security.LabIdentities;
import dev.errorlens.infrastructure.simulator.SimulatorControlClient;

@ExtendWith(MockitoExtension.class)
class ScenarioRunnerTest {

    @Mock
    ScenarioRepository scenarios;
    @Mock
    ScenarioRegistry registry;
    @Mock
    LabRequestDispatcher dispatcher;
    @Mock
    SimulatorControlClient simulator;
    @Mock
    ExecutionPersistenceService persistence;

    private ScenarioRunner runner;

    @BeforeEach
    void setUp() {
        ErrorLensProperties properties = new ErrorLensProperties(new ErrorLensProperties.Lab(true),
                new ErrorLensProperties.Runner("", java.time.Duration.ofSeconds(5), 2), null, null, null, null, null);
        runner = new ScenarioRunner(scenarios, registry, mock(ScenarioSupport.class), dispatcher, simulator,
                new ExecutionRecorder(), persistence, mock(LabIdentities.class), properties);
    }

    @Test
    void rejectsUnknownScenario() {
        when(scenarios.findById("Z99")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runner.execute("Z99", ExecutionMode.DEFAULT, "t", null))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(dispatcher, never()).dispatch(any(), any());
    }

    @Test
    void rejectsUnsupportedMode() {
        Scenario scenario = mock(Scenario.class);
        when(scenario.isEnabled()).thenReturn(true);
        when(scenarios.findById("A03")).thenReturn(Optional.of(scenario));
        when(registry.find("A03")).thenReturn(Optional.of(ScenarioHandler.single("A03",
                (mode, support) -> ScenarioPlan.request("GET", "/x").build())));
        assertThatThrownBy(() -> runner.execute("A03", ExecutionMode.FAILURE, "t", null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("não suporta o modo");
        verify(persistence, never()).persist(any());
    }

    @Test
    void refusesExecutionWhenLabIsDisabled() {
        ErrorLensProperties disabled = new ErrorLensProperties(new ErrorLensProperties.Lab(false),
                new ErrorLensProperties.Runner("", java.time.Duration.ofSeconds(5), 2), null, null, null, null, null);
        ScenarioRunner prodRunner = new ScenarioRunner(scenarios, registry, mock(ScenarioSupport.class), dispatcher,
                simulator, new ExecutionRecorder(), persistence, mock(LabIdentities.class), disabled);
        assertThatThrownBy(() -> prodRunner.execute("A01", ExecutionMode.DEFAULT, "t", null))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("desabilitada");
    }
}
