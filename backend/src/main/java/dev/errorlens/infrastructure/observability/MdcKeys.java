package dev.errorlens.infrastructure.observability;

/**
 * Chaves de MDC e headers usados na correlação de execuções.
 */
public final class MdcKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String EXECUTION_KEY = "executionKey";
    public static final String SCENARIO = "scenario";

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String EXECUTION_HEADER = "X-ErrorLens-Execution";
    public static final String SIMULATION_KEY_HEADER = "X-Simulation-Key";
    public static final String DEGRADED_HEADER = "X-ErrorLens-Degraded";

    private MdcKeys() {
    }
}
