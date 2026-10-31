package dev.errorlens.application.analyzer;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.errorlens.config.ErrorLensProperties;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.infrastructure.observability.SensitiveDataSanitizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analisa logs estruturados (JSON por linha) e textuais: níveis, correlationId/traceId,
 * exceptions, agrupamentos e linha do tempo. Arquivos são validados (extensão, tamanho,
 * codificação UTF-8) e o conteúdo é sanitizado.
 */
@Service
public class LogAnalyzer {

    private static final Set<String> EXTENSIONS = Set.of("log", "txt", "json");
    private static final int MAX_LINES = 50_000;
    private static final int MAX_GROUPS = 200;
    private static final Pattern TIMESTAMP = Pattern.compile(
            "^\\[?(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?:Z|[+-]\\d{2}:?\\d{2})?)\\]?");
    private static final Pattern LEVEL = Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    private static final Pattern THREAD = Pattern.compile("\\[([\\w.\\-#: ]{2,60})\\]");
    private static final Pattern LOGGER = Pattern.compile("\\s((?:[a-zA-Z_$][\\w$]*\\.)+[A-Z][\\w$]*)\\s*[:-]\\s");
    private static final Pattern CORRELATION = Pattern.compile(
            "(?i)(?:correlation[_-]?id|x-correlation-id|cid)\"?\\s*[=:]\\s*\"?([A-Za-z0-9._-]{8,64})");
    private static final Pattern TRACE = Pattern.compile("(?i)trace[_-]?id\"?\\s*[=:]\\s*\"?([0-9a-f]{16,32})");
    private static final Pattern EXCEPTION = Pattern.compile(
            "((?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error)(?:\\$[\\w$]+)?)");

    private final JsonMapper mapper;
    private final ErrorLensProperties properties;

    public LogAnalyzer(JsonMapper mapper, ErrorLensProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public LogAnalysis analyzeFile(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!EXTENSIONS.contains(extension)) {
            throw new InvalidInputException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE",
                    "Tipo de arquivo não suportado. Envie arquivos .log, .txt ou .json.");
        }
        if (bytes.length > properties.analyzer().maxLogBytes()) {
            throw new InvalidInputException(HttpStatus.CONTENT_TOO_LARGE, "CONTENT_TOO_LARGE",
                    "O arquivo excede o limite de " + properties.analyzer().maxLogBytes() / 1024 + " KB.");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new InvalidInputException("INVALID_ENCODING", "O arquivo não é texto UTF-8 válido.");
        }
        if (content.indexOf('\0') >= 0) {
            throw new InvalidInputException("BINARY_CONTENT", "O arquivo parece ser binário.");
        }
        return analyze(content, filename);
    }

    public LogAnalysis analyze(String content, String source) {
        if (content == null || content.isBlank()) {
            throw new InvalidInputException("EMPTY_CONTENT", "Informe o conteúdo dos logs para análise.");
        }
        if (content.length() > properties.analyzer().maxLogBytes()) {
            throw new InvalidInputException(HttpStatus.CONTENT_TOO_LARGE, "CONTENT_TOO_LARGE",
                    "O conteúdo excede o limite permitido.");
        }
        String[] lines = content.replace("\r\n", "\n").split("\n");
        if (lines.length > MAX_LINES) {
            throw new InvalidInputException(HttpStatus.CONTENT_TOO_LARGE, "TOO_MANY_LINES",
                    "O conteúdo excede " + MAX_LINES + " linhas.");
        }
        int maxEntries = properties.analyzer().maxLogEntries();
        List<LogEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int jsonLines = 0;
        int textLines = 0;
        int orphanLines = 0;
        boolean truncated = false;
        LogEntry previous = null;

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            LogEntry entry = null;
            if (trimmed.startsWith("{")) {
                entry = parseJson(entries.size(), trimmed);
                if (entry != null) {
                    jsonLines++;
                }
            }
            if (entry == null && TIMESTAMP.matcher(trimmed).find()) {
                entry = parseText(entries.size(), trimmed);
                textLines++;
            }
            if (entry == null) {
                if (previous != null) {
                    previous.appendContinuation(SensitiveDataSanitizer.text(line, 2000));
                } else {
                    orphanLines++;
                }
                continue;
            }
            if (entries.size() >= maxEntries) {
                truncated = true;
                break;
            }
            entries.add(entry);
            previous = entry;
        }
        if (truncated) {
            warnings.add("Apenas as primeiras " + maxEntries + " entradas foram analisadas.");
        }
        if (orphanLines > 0) {
            warnings.add(orphanLines + " linha(s) antes da primeira entrada reconhecida foram ignoradas.");
        }
        if (entries.isEmpty()) {
            warnings.add("Nenhuma entrada de log reconhecida (esperado timestamp ISO-8601 ou JSON por linha).");
        }
        entries.forEach(LogEntry::finish);

        Map<String, Long> levels = new LinkedHashMap<>();
        for (String level : List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR")) {
            levels.put(level, entries.stream().filter(e -> level.equals(e.level)).count());
        }
        Map<String, List<LogEntry>> byCorrelation = new LinkedHashMap<>();
        entries.stream().filter(e -> e.correlationId != null)
                .forEach(e -> byCorrelation.computeIfAbsent(e.correlationId, k -> new ArrayList<>()).add(e));
        List<CorrelationGroup> groups = byCorrelation.entrySet().stream().map(e -> group(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CorrelationGroup::errorCount).reversed()
                        .thenComparing(Comparator.comparingInt(CorrelationGroup::count).reversed()))
                .limit(MAX_GROUPS)
                .toList();
        Map<String, ExceptionStat> exceptions = new TreeMap<>();
        for (LogEntry entry : entries) {
            if (entry.exceptionClass != null) {
                exceptions.computeIfAbsent(entry.exceptionClass, k -> new ExceptionStat(k, 0, entry.timestamp,
                        new LinkedHashSet<>())).register(entry);
            }
        }
        Instant from = entries.stream().map(e -> e.timestamp).filter(t -> t != null).min(Instant::compareTo)
                .orElse(null);
        Instant to = entries.stream().map(e -> e.timestamp).filter(t -> t != null).max(Instant::compareTo)
                .orElse(null);
        String format = jsonLines > 0 && textLines > 0 ? "MIXED" : jsonLines > 0 ? "JSON" : "TEXT";
        return new LogAnalysis(source, format, lines.length, entries.size(), truncated, levels, groups,
                exceptions.values().stream().sorted(Comparator.comparingInt(ExceptionStat::count).reversed())
                        .map(ExceptionStat::summary).toList(),
                from, to, entries.stream().map(LogEntry::view).toList(), warnings);
    }

    private LogEntry parseJson(int index, String line) {
        JsonNode node;
        try {
            node = mapper.readTree(line);
        } catch (JacksonException ex) {
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        LogEntry entry = new LogEntry(index);
        entry.timestamp = parseTime(first(node, "@timestamp", "timestamp", "time", "ts"));
        String level = first(node, "level", "log.level", "severity", "levelname");
        entry.level = normalizeLevel(level);
        entry.logger = first(node, "logger_name", "logger", "log.logger", "loggerName");
        entry.thread = first(node, "thread_name", "thread", "process.thread.name");
        entry.message = SensitiveDataSanitizer.text(first(node, "message", "msg", "@message"), 4000);
        entry.correlationId = first(node, "correlationId", "correlation_id", "correlation.id", "cid");
        entry.traceId = first(node, "traceId", "trace_id", "trace.id");
        String stack = first(node, "stack_trace", "stacktrace", "exception", "error.stack_trace");
        if (stack != null) {
            entry.appendContinuation(SensitiveDataSanitizer.text(stack, 20_000));
        }
        String exceptionClass = first(node, "exception_class", "error.type");
        if (exceptionClass != null) {
            entry.exceptionClass = exceptionClass;
        }
        return entry;
    }

    private LogEntry parseText(int index, String line) {
        LogEntry entry = new LogEntry(index);
        Matcher ts = TIMESTAMP.matcher(line);
        String rest = line;
        if (ts.find()) {
            entry.timestamp = parseTime(ts.group(1));
            rest = line.substring(ts.end());
        }
        String head = rest.length() > 120 ? rest.substring(0, 120) : rest;
        Matcher level = LEVEL.matcher(head);
        if (level.find()) {
            entry.level = normalizeLevel(level.group(1));
        }
        Matcher thread = THREAD.matcher(head);
        if (thread.find()) {
            entry.thread = thread.group(1);
        }
        Matcher logger = LOGGER.matcher(rest);
        String message = rest;
        if (logger.find()) {
            entry.logger = logger.group(1);
            message = rest.substring(logger.end());
        }
        entry.message = SensitiveDataSanitizer.text(message.trim(), 4000);
        Matcher cid = CORRELATION.matcher(line);
        if (cid.find()) {
            entry.correlationId = cid.group(1);
        }
        Matcher trace = TRACE.matcher(line);
        if (trace.find()) {
            entry.traceId = trace.group(1);
        }
        return entry;
    }

    private static String first(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null && name.contains(".")) {
                String[] parts = name.split("\\.");
                value = node;
                for (String part : parts) {
                    value = value == null ? null : value.get(part);
                }
            }
            if (value != null && !value.isNull()) {
                return value.isValueNode() ? value.asString() : value.toString();
            }
        }
        return null;
    }

    static Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replace(',', '.');
        try {
            return OffsetDateTime.parse(v.replace(' ', 'T')).toInstant();
        } catch (DateTimeParseException ignored) {
            // tenta sem offset
        }
        try {
            String iso = v.replace(' ', 'T');
            if (iso.matches(".*[+-]\\d{4}$")) {
                iso = iso.substring(0, iso.length() - 2) + ":" + iso.substring(iso.length() - 2);
                return OffsetDateTime.parse(iso).toInstant();
            }
            return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String normalizeLevel(String level) {
        if (level == null) {
            return "INFO";
        }
        String upper = level.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "WARNING" -> "WARN";
            case "FATAL", "CRITICAL", "SEVERE" -> "ERROR";
            default -> upper;
        };
    }

    private static CorrelationGroup group(String correlationId, List<LogEntry> entries) {
        Instant first = entries.stream().map(e -> e.timestamp).filter(t -> t != null).min(Instant::compareTo)
                .orElse(null);
        Instant last = entries.stream().map(e -> e.timestamp).filter(t -> t != null).max(Instant::compareTo)
                .orElse(null);
        Set<String> exceptions = new LinkedHashSet<>();
        Set<String> loggers = new LinkedHashSet<>();
        Set<String> traces = new LinkedHashSet<>();
        for (LogEntry e : entries) {
            if (e.exceptionClass != null) {
                exceptions.add(e.exceptionClass);
            }
            if (e.logger != null) {
                loggers.add(e.logger);
            }
            if (e.traceId != null) {
                traces.add(e.traceId);
            }
        }
        return new CorrelationGroup(correlationId, entries.size(),
                entries.stream().filter(e -> "ERROR".equals(e.level)).count(),
                entries.stream().filter(e -> "WARN".equals(e.level)).count(), first, last,
                first == null || last == null ? null : Duration.between(first, last).toMillis(),
                List.copyOf(exceptions), List.copyOf(loggers), List.copyOf(traces),
                entries.stream().map(e -> e.index).toList());
    }

    private static final class LogEntry {
        private final int index;
        private Instant timestamp;
        private String level = "INFO";
        private String logger;
        private String thread;
        private String message;
        private String correlationId;
        private String traceId;
        private String exceptionClass;
        private StringBuilder continuation;

        private LogEntry(int index) {
            this.index = index;
        }

        void appendContinuation(String line) {
            if (continuation == null) {
                continuation = new StringBuilder();
            } else {
                continuation.append('\n');
            }
            if (continuation.length() < 20_000) {
                continuation.append(line);
            }
        }

        void finish() {
            if (exceptionClass == null) {
                String source = (message == null ? "" : message) + "\n" + (continuation == null ? "" : continuation);
                Matcher m = EXCEPTION.matcher(source);
                if (m.find()) {
                    exceptionClass = m.group(1);
                }
            }
        }

        EntryView view() {
            return new EntryView(index, timestamp, level, logger, thread, message, correlationId, traceId,
                    exceptionClass, continuation == null ? null : continuation.toString());
        }
    }

    private static final class ExceptionStat {
        private final String className;
        private int count;
        private final Instant firstSeen;
        private final Set<String> correlationIds;

        private ExceptionStat(String className, int count, Instant firstSeen, Set<String> correlationIds) {
            this.className = className;
            this.count = count;
            this.firstSeen = firstSeen;
            this.correlationIds = correlationIds;
        }

        void register(LogEntry entry) {
            count++;
            if (entry.correlationId != null && correlationIds.size() < 20) {
                correlationIds.add(entry.correlationId);
            }
        }

        int count() {
            return count;
        }

        ExceptionSummary summary() {
            return new ExceptionSummary(className, count, firstSeen, List.copyOf(correlationIds));
        }
    }

    public record ExceptionSummary(String className, int count, Instant firstSeen, List<String> correlationIds) {
    }

    public record EntryView(int index, Instant timestamp, String level, String logger, String thread, String message,
                            String correlationId, String traceId, String exceptionClass, String details) {
    }

    public record CorrelationGroup(String correlationId, int count, long errorCount, long warnCount, Instant first,
                                   Instant last, Long durationMs, List<String> exceptions, List<String> loggers,
                                   List<String> traceIds, List<Integer> entryIndexes) {
    }

    public record LogAnalysis(String source, String format, int totalLines, int totalEntries, boolean truncated,
                              Map<String, Long> levelCounts, List<CorrelationGroup> groups,
                              List<ExceptionSummary> exceptions, Instant from, Instant to, List<EntryView> entries,
                              List<String> warnings) {
    }
}
