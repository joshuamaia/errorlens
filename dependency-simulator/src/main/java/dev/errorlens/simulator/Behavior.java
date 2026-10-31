package dev.errorlens.simulator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Comportamento configurado para uma chave de simulação.
 */
public record Behavior(
        @NotNull Mode mode,
        @Min(0) @Max(10_000) int delayMs,
        @Min(0) @Max(50) int failures,
        @Min(200) @Max(599) int status) {

    public enum Mode {
        IMMEDIATE, SLOW, ERROR_500, ERROR_503, FAIL_FIRST_N, UNAVAILABLE
    }

    public static Behavior immediate() {
        return new Behavior(Mode.IMMEDIATE, 0, 0, 200);
    }
}
