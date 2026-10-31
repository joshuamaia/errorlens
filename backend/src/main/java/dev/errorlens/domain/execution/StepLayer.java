package dev.errorlens.domain.execution;

/**
 * Camadas instrumentadas que podem aparecer na timeline de uma execução.
 */
public enum StepLayer {
    RUNNER("Scenario Runner"),
    HTTP("HTTP Request"),
    SECURITY("Spring Security"),
    CONTROLLER("Controller"),
    SERVICE("Service"),
    REPOSITORY("Repository"),
    DATABASE("PostgreSQL"),
    EXTERNAL("Chamada externa"),
    RESILIENCE("Resilience4j"),
    EXCEPTION_HANDLER("Exception Handler"),
    RESPONSE("HTTP Response"),
    VERIFICATION("Verificação");

    private final String label;

    StepLayer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
