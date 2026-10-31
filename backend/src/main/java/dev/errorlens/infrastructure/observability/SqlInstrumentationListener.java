package dev.errorlens.infrastructure.observability;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.errorlens.domain.execution.StepLayer;
import dev.errorlens.domain.execution.StepStatus;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

/**
 * Listener do datasource-proxy que registra cada comando SQL (com tempo medido pelo proxy JDBC)
 * e os eventos de transação (BEGIN/COMMIT/ROLLBACK) na execução ativa.
 * Apenas o texto do comando com placeholders é registrado; valores de parâmetros nunca são capturados.
 */
public class SqlInstrumentationListener implements QueryExecutionListener, MethodExecutionListener {

    private static final Pattern VERB = Pattern.compile("^\\s*(\\w+)");
    private static final Pattern TABLE = Pattern.compile("(?i)\\b(?:from|into|update|join)\\s+([a-zA-Z_][\\w.]*)");

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // nada a fazer: a duração é obtida em afterQuery
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        if (!ExecutionContextHolder.isActive() || queryInfoList.isEmpty()) {
            return;
        }
        String sql = queryInfoList.getFirst().getQuery();
        String label = describe(sql);
        Throwable error = execInfo.getThrowable();
        String errorMessage = null;
        String errorClass = null;
        if (error != null) {
            errorClass = error.getClass().getName();
            String sqlState = error instanceof SQLException sqlEx ? sqlEx.getSQLState() : null;
            errorMessage = (sqlState == null ? "" : "SQLState " + sqlState + ": ")
                    + SensitiveDataSanitizer.text(error.getMessage(), 1000);
        }
        String detail = SensitiveDataSanitizer.truncate(sql.strip(), 600)
                + (execInfo.isBatch() ? " [batch, " + execInfo.getBatchSize() + " comandos]" : "");
        ExecutionContextHolder.timedEvent(StepLayer.DATABASE, "PostgreSQL · " + label, "JDBC (datasource-proxy)",
                error == null ? StepStatus.OK : StepStatus.ERROR,
                execInfo.getElapsedTime() * 1000L, detail, errorClass, errorMessage);
    }

    @Override
    public void beforeMethod(MethodExecutionContext executionContext) {
        // eventos registrados após a chamada
    }

    @Override
    public void afterMethod(MethodExecutionContext ctx) {
        if (!ExecutionContextHolder.isActive() || !(ctx.getTarget() instanceof Connection)) {
            return;
        }
        String method = ctx.getMethod().getName();
        Object[] args = ctx.getMethodArgs();
        String event = switch (method) {
            case "commit" -> "COMMIT";
            case "rollback" -> args == null || args.length == 0 ? "ROLLBACK" : null;
            case "setAutoCommit" -> args != null && args.length == 1 && Boolean.FALSE.equals(args[0])
                    ? "BEGIN (início da transação)" : null;
            default -> null;
        };
        if (event == null) {
            return;
        }
        Throwable error = ctx.getThrown();
        StepStatus status = error != null ? StepStatus.ERROR
                : "ROLLBACK".equals(event) ? StepStatus.WARN : StepStatus.INFO;
        ExecutionContextHolder.timedEvent(StepLayer.DATABASE, "PostgreSQL · " + event, "JDBC Connection", status,
                ctx.getElapsedTime() * 1000L,
                "ROLLBACK".equals(event) ? "Transação revertida: alterações não confirmadas foram descartadas" : null,
                error == null ? null : error.getClass().getName(),
                error == null ? null : SensitiveDataSanitizer.text(error.getMessage(), 500));
    }

    static String describe(String sql) {
        Matcher verb = VERB.matcher(sql);
        String operation = verb.find() ? verb.group(1).toUpperCase(Locale.ROOT) : "SQL";
        Matcher table = TABLE.matcher(sql);
        return table.find() ? operation + " " + table.group(1) : operation;
    }
}
