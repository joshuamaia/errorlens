package dev.errorlens.application.execution;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.infrastructure.observability.SensitiveDataSanitizer;

/**
 * Utilitários para extrair a cadeia de causas e o stack trace sanitizado de uma exception.
 */
public final class ExceptionChains {

    static final int MAX_DEPTH = 12;
    static final int MAX_STACK_TRACE_CHARS = 60_000;

    private ExceptionChains() {
    }

    public static List<ExceptionInfo> chain(Throwable throwable) {
        List<ExceptionInfo> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && seen.add(current) && chain.size() < MAX_DEPTH) {
            chain.add(new ExceptionInfo(current.getClass().getName(),
                    SensitiveDataSanitizer.text(current.getMessage(), 2000)));
            current = current.getCause();
        }
        return chain;
    }

    public static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return SensitiveDataSanitizer.text(writer.toString(), MAX_STACK_TRACE_CHARS);
    }
}
