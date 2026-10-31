package dev.errorlens.application.scenario;

import java.util.List;

/**
 * Resultado de uma verificação automática pós-execução, baseada em consultas reais ao banco.
 */
public record Verification(String name, String conclusion, boolean passed, List<Check> checks) {

    public record Check(String description, String expected, String actual, boolean passed) {

        public static Check of(String description, Object expected, Object actual) {
            return new Check(description, String.valueOf(expected), String.valueOf(actual),
                    String.valueOf(expected).equals(String.valueOf(actual)));
        }
    }

    public static Verification of(String name, String successConclusion, String failureConclusion,
                                  List<Check> checks) {
        boolean passed = checks.stream().allMatch(Check::passed);
        return new Verification(name, passed ? successConclusion : failureConclusion, passed, checks);
    }
}
