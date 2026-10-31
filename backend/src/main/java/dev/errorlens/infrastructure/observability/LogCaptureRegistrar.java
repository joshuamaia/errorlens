package dev.errorlens.infrastructure.observability;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.LoggerContext;

/**
 * Registra o {@link ExecutionLogCaptureAppender} no logger raiz após a inicialização do
 * sistema de logging do Spring Boot.
 */
@Component
public class LogCaptureRegistrar {

    private static final String APPENDER_NAME = "ERRORLENS_EXECUTION_CAPTURE";

    private ExecutionLogCaptureAppender appender;

    @PostConstruct
    void register() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            return;
        }
        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (root.getAppender(APPENDER_NAME) != null) {
            return;
        }
        appender = new ExecutionLogCaptureAppender();
        appender.setName(APPENDER_NAME);
        appender.setContext(context);
        appender.start();
        root.addAppender(appender);
    }

    @PreDestroy
    void unregister() {
        if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            appender.stop();
        }
    }
}
