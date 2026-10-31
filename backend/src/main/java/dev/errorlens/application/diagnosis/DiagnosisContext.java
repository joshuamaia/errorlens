package dev.errorlens.application.diagnosis;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Evidências disponíveis para o Diagnosis Engine. Pode ser montado a partir de uma execução
 * instrumentada, de um stack trace importado, de uma resposta HTTP ou de logs.
 *
 * @param exceptions cadeia de exceptions, da mais externa (índice 0) até a root cause (último)
 */
public record DiagnosisContext(
        Source source,
        Integer httpStatus,
        String errorCode,
        List<ExceptionInfo> exceptions,
        List<StepInfo> steps,
        List<LogInfo> logs,
        List<AttemptInfo> attempts,
        String outcome,
        Long durationMs,
        Map<String, String> responseHeaders) {

    public enum Source {
        EXECUTION, STACKTRACE, HTTP_ERROR, LOG
    }

    public record ExceptionInfo(String className, String message) {
        public String simpleName() {
            int idx = className.lastIndexOf('.');
            String simple = idx < 0 ? className : className.substring(idx + 1);
            int inner = simple.lastIndexOf('$');
            return inner < 0 ? simple : simple.substring(inner + 1);
        }
    }

    public record StepInfo(String layer, String name, String status, String errorClass, String errorMessage,
                           String detail) {
    }

    public record LogInfo(String level, String message, String exceptionClass) {
    }

    public record AttemptInfo(int number, String outcome, Integer httpStatus, String errorClass,
                              String circuitState) {
    }

    public DiagnosisContext {
        exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
        steps = steps == null ? List.of() : List.copyOf(steps);
        logs = logs == null ? List.of() : List.copyOf(logs);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
        responseHeaders = responseHeaders == null ? Map.of() : Map.copyOf(responseHeaders);
    }

    public static DiagnosisContext ofExceptions(Source source, Integer status, List<ExceptionInfo> exceptions) {
        return new DiagnosisContext(source, status, null, exceptions, null, null, null, null, null, null);
    }

    public ExceptionInfo rootCause() {
        return exceptions.isEmpty() ? null : exceptions.getLast();
    }

    public ExceptionInfo primary() {
        return exceptions.isEmpty() ? null : exceptions.getFirst();
    }

    /**
     * Todos os textos onde padrões podem ser procurados (mensagens de exceptions, erros de etapas e logs).
     */
    public Stream<String> searchableTexts() {
        return Stream.of(
                        exceptions.stream().map(ExceptionInfo::message),
                        steps.stream().map(StepInfo::errorMessage),
                        steps.stream().map(StepInfo::detail),
                        logs.stream().map(LogInfo::message))
                .flatMap(s -> s)
                .filter(text -> text != null && !text.isBlank());
    }
}
