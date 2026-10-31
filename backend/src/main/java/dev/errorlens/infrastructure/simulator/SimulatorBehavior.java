package dev.errorlens.infrastructure.simulator;

/**
 * Comportamento configurável do dependency-simulator para uma chave de simulação.
 */
public record SimulatorBehavior(Mode mode, int delayMs, int failures, int status) {

    public enum Mode {
        IMMEDIATE, SLOW, ERROR_500, ERROR_503, FAIL_FIRST_N, UNAVAILABLE
    }

    public static SimulatorBehavior immediate() {
        return new SimulatorBehavior(Mode.IMMEDIATE, 0, 0, 200);
    }

    public static SimulatorBehavior slow(int delayMs) {
        return new SimulatorBehavior(Mode.SLOW, delayMs, 0, 200);
    }

    public static SimulatorBehavior error500() {
        return new SimulatorBehavior(Mode.ERROR_500, 0, 0, 500);
    }

    public static SimulatorBehavior error503() {
        return new SimulatorBehavior(Mode.ERROR_503, 0, 0, 503);
    }

    public static SimulatorBehavior failFirst(int failures) {
        return new SimulatorBehavior(Mode.FAIL_FIRST_N, 0, failures, 503);
    }
}
