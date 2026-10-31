package dev.errorlens.application.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.errorlens.application.execution.ComparisonService.AlignedStep;
import dev.errorlens.application.execution.ExecutionViews.StepView;

class ComparisonAlignmentTest {

    private static StepView step(int seq, String layer, String name, String status, long offset, Long duration) {
        return new StepView(seq, layer, layer, name, null, 0, status, Instant.EPOCH, offset, duration, null, null,
                null);
    }

    @Test
    void alignsCommonStepsAndFindsEarliestObservableDivergence() {
        List<StepView> success = List.of(
                step(1, "RUNNER", "Envio", "OK", 0, 1000L),
                step(2, "CONTROLLER", "CustomerController.create", "OK", 100, 800L),
                step(3, "DATABASE", "PostgreSQL · INSERT customers", "OK", 200, 50L),
                step(4, "RESPONSE", "HTTP Response", "OK", 950, null));
        List<StepView> failure = List.of(
                step(1, "RUNNER", "Envio", "WARN", 0, 1000L),
                step(2, "CONTROLLER", "CustomerController.create", "ERROR", 100, 800L),
                step(3, "DATABASE", "PostgreSQL · INSERT customers", "ERROR", 200, 50L),
                step(4, "EXCEPTION_HANDLER", "GlobalExceptionHandler.handleIntegrity", "WARN", 920, null),
                step(5, "RESPONSE", "HTTP Response", "WARN", 950, null));

        List<AlignedStep> aligned = ComparisonService.align(success, failure);

        assertThat(aligned).hasSize(5);
        assertThat(aligned.get(3).relation()).isEqualTo("RIGHT_ONLY");
        var divergence = ComparisonService.firstDivergence(aligned);
        assertThat(divergence).isNotNull();
        assertThat(divergence.layer()).isEqualTo("DATABASE");
        assertThat(divergence.description()).contains("INSERT customers").contains("ERROR");
    }

    @Test
    void reportsNoDivergenceForEquivalentFlows() {
        List<StepView> a = List.of(step(1, "HTTP", "HTTP Request", "OK", 0, 10L));
        assertThat(ComparisonService.firstDivergence(ComparisonService.align(a, a))).isNull();
    }
}
