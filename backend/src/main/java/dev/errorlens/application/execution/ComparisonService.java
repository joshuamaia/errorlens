package dev.errorlens.application.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.application.execution.ExecutionViews.StepView;
import dev.errorlens.application.execution.ExecutionViews.Summary;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.domain.execution.Execution;
import dev.errorlens.domain.execution.ExecutionLog;

/**
 * Compara duas execuções lado a lado: campos principais, fluxo instrumentado (alinhado por LCS),
 * corpos, headers, exceptions e logs. Aponta a primeira divergência observável sem afirmar
 * causalidade.
 */
@Service
@Transactional(readOnly = true)
public class ComparisonService {

    public static final String CAUSALITY_NOTE = "A primeira divergência observável indica onde os fluxos "
            + "instrumentados se separam, não necessariamente a causa. Diferenças anteriores não instrumentadas "
            + "(dados de entrada, estado do banco, configuração) podem ser a origem real.";
    private static final Set<String> VOLATILE_HEADERS = Set.of("date", "x-correlation-id", "x-errorlens-execution",
            "x-simulation-key", "keep-alive", "connection", "content-length", "location");
    private static final int MAX_DIFFS = 200;

    private final ExecutionQueryService queries;
    private final ExecutionMapper mapper;
    private final JsonText json;

    public ComparisonService(ExecutionQueryService queries, ExecutionMapper mapper, JsonText json) {
        this.queries = queries;
        this.mapper = mapper;
        this.json = json;
    }

    public Comparison compare(Long leftId, Long rightId) {
        if (Objects.equals(leftId, rightId)) {
            throw new InvalidInputException("SAME_EXECUTION", "Selecione duas execuções diferentes para comparar.");
        }
        Execution left = queries.require(leftId);
        Execution right = queries.require(rightId);
        Summary a = mapper.summary(left);
        Summary b = mapper.summary(right);

        List<FieldDiff> fields = List.of(
                field("scenario", "Cenário", a.scenarioCode() + " - " + a.scenarioName(),
                        b.scenarioCode() + " - " + b.scenarioName()),
                field("mode", "Modo", a.mode(), b.mode()),
                field("httpStatus", "Status HTTP", a.httpStatus(), b.httpStatus()),
                field("outcome", "Resultado", a.outcome(), b.outcome()),
                field("duration", "Duração (ms)", a.durationMs(), b.durationMs()),
                field("endpoint", "Endpoint", a.httpMethod() + " " + a.endpoint(), b.httpMethod() + " " + b.endpoint()),
                field("errorCode", "errorCode", a.errorCode(), b.errorCode()),
                field("exception", "Exception", a.exceptionClass(), b.exceptionClass()),
                field("rootCause", "Root cause", a.rootCauseClass(), b.rootCauseClass()),
                field("steps", "Etapas registradas", left.getSteps().size(), right.getSteps().size()),
                field("attempts", "Tentativas externas", left.getAttempts().size(), right.getAttempts().size()));

        List<StepView> leftSteps = left.getSteps().stream().map(mapper::step).toList();
        List<StepView> rightSteps = right.getSteps().stream().map(mapper::step).toList();
        List<AlignedStep> aligned = align(leftSteps, rightSteps);
        Divergence divergence = firstDivergence(aligned);

        List<JsonDiff> requestDiff = diffBodies(left.getRequestBody(), right.getRequestBody());
        List<JsonDiff> responseDiff = diffBodies(left.getResponseBody(), right.getResponseBody());
        List<FieldDiff> headerDiff = diffHeaders(json.map(left.getResponseHeaders()),
                json.map(right.getResponseHeaders()));

        Map<String, Map<String, Long>> logLevels = new LinkedHashMap<>();
        logLevels.put("left", levels(left.getLogs()));
        logLevels.put("right", levels(right.getLogs()));

        List<String> observations = observations(a, b, divergence, requestDiff, fields);
        return new Comparison(a, b, fields, aligned, divergence, requestDiff, responseDiff, headerDiff, logLevels,
                json.mapList(left.getExceptionChain()), json.mapList(right.getExceptionChain()),
                mapper.logs(left), mapper.logs(right), observations, CAUSALITY_NOTE);
    }

    static List<AlignedStep> align(List<StepView> left, List<StepView> right) {
        int n = left.size();
        int m = right.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = signature(left.get(i)).equals(signature(right.get(j)))
                        ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<AlignedStep> result = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            StepView l = left.get(i);
            StepView r = right.get(j);
            if (signature(l).equals(signature(r))) {
                result.add(new AlignedStep(l, r, l.status().equals(r.status()) ? "SAME" : "STATUS_DIFFERENT"));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                result.add(new AlignedStep(l, null, "LEFT_ONLY"));
                i++;
            } else {
                result.add(new AlignedStep(null, r, "RIGHT_ONLY"));
                j++;
            }
        }
        while (i < n) {
            result.add(new AlignedStep(left.get(i++), null, "LEFT_ONLY"));
        }
        while (j < m) {
            result.add(new AlignedStep(null, right.get(j++), "RIGHT_ONLY"));
        }
        return result;
    }

    /**
     * A divergência "observável" mais cedo: diferenças de status só são conhecidas no fim da etapa
     * (etapas-pai terminam depois das filhas); etapas exclusivas são observadas no seu início.
     */
    static Divergence firstDivergence(List<AlignedStep> aligned) {
        int best = -1;
        long bestAt = Long.MAX_VALUE;
        for (int index = 0; index < aligned.size(); index++) {
            AlignedStep row = aligned.get(index);
            if ("SAME".equals(row.relation())) {
                continue;
            }
            long observedAt = "STATUS_DIFFERENT".equals(row.relation())
                    ? Math.min(end(row.left()), end(row.right()))
                    : (row.left() != null ? row.left() : row.right()).offsetMicros();
            if (observedAt < bestAt) {
                bestAt = observedAt;
                best = index;
            }
        }
        if (best >= 0) {
            int index = best;
            AlignedStep row = aligned.get(index);
            StepView ref = row.left() != null ? row.left() : row.right();
            String description = switch (row.relation()) {
                case "STATUS_DIFFERENT" -> "A etapa \"" + ref.name() + "\" (" + ref.layerLabel() + ") terminou com "
                        + row.left().status() + " na execução A e " + row.right().status() + " na execução B.";
                case "LEFT_ONLY" -> "A etapa \"" + ref.name() + "\" (" + ref.layerLabel()
                        + ") aparece apenas na execução A.";
                default -> "A etapa \"" + ref.name() + "\" (" + ref.layerLabel() + ") aparece apenas na execução B.";
            };
            return new Divergence(index, ref.layer(), ref.layerLabel(), description);
        }
        return null;
    }

    private static long end(StepView step) {
        return step.offsetMicros() + (step.durationMicros() == null ? 0 : step.durationMicros());
    }

    private List<JsonDiff> diffBodies(String leftBody, String rightBody) {
        Object l = json.tryParse(leftBody);
        Object r = json.tryParse(rightBody);
        if (l == null || r == null) {
            if (Objects.equals(leftBody, rightBody)) {
                return List.of();
            }
            return List.of(new JsonDiff("$", abbreviate(leftBody), abbreviate(rightBody),
                    leftBody == null ? "ADDED" : rightBody == null ? "REMOVED" : "CHANGED"));
        }
        Map<String, String> fl = new TreeMap<>();
        Map<String, String> fr = new TreeMap<>();
        flatten("$", l, fl);
        flatten("$", r, fr);
        Set<String> paths = new TreeSet<>(fl.keySet());
        paths.addAll(fr.keySet());
        List<JsonDiff> diffs = new ArrayList<>();
        for (String path : paths) {
            String lv = fl.get(path);
            String rv = fr.get(path);
            if (!Objects.equals(lv, rv)) {
                diffs.add(new JsonDiff(path, lv, rv, lv == null ? "ADDED" : rv == null ? "REMOVED" : "CHANGED"));
            }
            if (diffs.size() >= MAX_DIFFS) {
                break;
            }
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String path, Object value, Map<String, String> out) {
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                out.put(path, "{}");
            }
            ((Map<String, Object>) map).forEach((k, v) -> flatten(path + "." + k, v, out));
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                out.put(path, "[]");
            }
            for (int i = 0; i < list.size(); i++) {
                flatten(path + "[" + i + "]", list.get(i), out);
            }
        } else {
            out.put(path, String.valueOf(value));
        }
    }

    private static List<FieldDiff> diffHeaders(Map<String, Object> left, Map<String, Object> right) {
        Map<String, String> l = lowerKeys(left);
        Map<String, String> r = lowerKeys(right);
        Set<String> names = new TreeSet<>(l.keySet());
        names.addAll(r.keySet());
        List<FieldDiff> diffs = new ArrayList<>();
        for (String name : names) {
            if (VOLATILE_HEADERS.contains(name)) {
                continue;
            }
            diffs.add(field(name, name, l.get(name), r.get(name)));
        }
        return diffs;
    }

    private static Map<String, String> lowerKeys(Map<String, Object> map) {
        return map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT),
                e -> String.valueOf(e.getValue()), (x, y) -> x, TreeMap::new));
    }

    private static Map<String, Long> levels(List<ExecutionLog> logs) {
        Map<String, Long> counts = new TreeMap<>(logs.stream()
                .collect(Collectors.groupingBy(ExecutionLog::getLevel, Collectors.counting())));
        for (String level : List.of("INFO", "WARN", "ERROR")) {
            counts.putIfAbsent(level, 0L);
        }
        return counts;
    }

    private static List<String> observations(Summary a, Summary b, Divergence divergence, List<JsonDiff> requestDiff,
                                             List<FieldDiff> fields) {
        List<String> notes = new ArrayList<>();
        if (!Objects.equals(a.scenarioCode(), b.scenarioCode())) {
            notes.add("As execuções pertencem a cenários diferentes (" + a.scenarioCode() + " e " + b.scenarioCode()
                    + "); diferenças de fluxo são esperadas.");
        }
        if (!Objects.equals(a.httpStatus(), b.httpStatus())) {
            notes.add("Status HTTP diferente: " + a.httpStatus() + " (A) vs " + b.httpStatus() + " (B).");
        }
        if (!requestDiff.isEmpty()) {
            notes.add("Os corpos das requisições diferem em " + requestDiff.size()
                    + " campo(s). Diferenças de entrada antecedem todo o fluxo instrumentado.");
        }
        if (a.exceptionClass() == null && b.exceptionClass() != null) {
            notes.add("Somente a execução B registrou exception: " + simple(b.exceptionClass())
                    + (b.rootCauseClass() != null && !b.rootCauseClass().equals(b.exceptionClass())
                    ? " (root cause " + simple(b.rootCauseClass()) + ")" : "") + ".");
        } else if (a.exceptionClass() != null && b.exceptionClass() == null) {
            notes.add("Somente a execução A registrou exception: " + simple(a.exceptionClass()) + ".");
        } else if (a.exceptionClass() != null && !a.exceptionClass().equals(b.exceptionClass())) {
            notes.add("Exceptions diferentes: " + simple(a.exceptionClass()) + " (A) vs "
                    + simple(b.exceptionClass()) + " (B).");
        }
        if (a.durationMs() != null && b.durationMs() != null) {
            long delta = b.durationMs() - a.durationMs();
            notes.add("Duração: " + a.durationMs() + " ms (A) vs " + b.durationMs() + " ms (B), diferença de "
                    + (delta >= 0 ? "+" : "") + delta + " ms.");
        }
        if (divergence != null) {
            notes.add("Primeira divergência observável na camada " + divergence.layerLabel() + ": "
                    + divergence.description());
        } else {
            notes.add("Os fluxos instrumentados são equivalentes (mesmas etapas e status).");
        }
        long different = fields.stream().filter(FieldDiff::different).count();
        notes.add(different + " de " + fields.size() + " campos principais diferem.");
        return Collections.unmodifiableList(notes);
    }

    private static String signature(StepView step) {
        return step.layer() + "|" + step.name();
    }

    private static FieldDiff field(String key, String label, Object left, Object right) {
        String l = left == null ? null : String.valueOf(left);
        String r = right == null ? null : String.valueOf(right);
        return new FieldDiff(key, label, l, r, !Objects.equals(l, r));
    }

    private static String simple(String className) {
        return className == null ? null : className.substring(className.lastIndexOf('.') + 1);
    }

    private static String abbreviate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500) + "…";
    }

    public record FieldDiff(String field, String label, String left, String right, boolean different) {
    }

    public record AlignedStep(StepView left, StepView right, String relation) {
    }

    public record Divergence(int index, String layer, String layerLabel, String description) {
    }

    public record JsonDiff(String path, String left, String right, String kind) {
    }

    public record Comparison(Summary left, Summary right, List<FieldDiff> fields, List<AlignedStep> steps,
                             Divergence firstDivergence, List<JsonDiff> requestDiff, List<JsonDiff> responseDiff,
                             List<FieldDiff> headerDiff, Map<String, Map<String, Long>> logLevels,
                             List<Map<String, Object>> leftExceptions, List<Map<String, Object>> rightExceptions,
                             List<ExecutionViews.LogView> leftLogs, List<ExecutionViews.LogView> rightLogs,
                             List<String> observations, String causalityNote) {
    }
}
