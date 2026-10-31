package dev.errorlens.infrastructure.observability;

import java.time.Instant;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * Appender do Logback que copia (sanitizados) os logs emitidos com o MDC {@code executionKey}
 * para o {@link ExecutionTrace} correspondente, permitindo consultar os logs correlacionados
 * de cada execução no frontend.
 */
public class ExecutionLogCaptureAppender extends AppenderBase<ILoggingEvent> {

    @Override
    protected void append(ILoggingEvent event) {
        String key = event.getMDCPropertyMap().get(MdcKeys.EXECUTION_KEY);
        if (key == null) {
            return;
        }
        ExecutionTrace trace = ExecutionRecorder.lookupStatic(key);
        if (trace == null) {
            return;
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        trace.addLog(Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                SensitiveDataSanitizer.text(event.getFormattedMessage(), 4000),
                throwable == null ? null : throwable.getClassName());
    }
}
