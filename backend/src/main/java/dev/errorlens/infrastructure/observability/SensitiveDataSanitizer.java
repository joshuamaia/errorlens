package dev.errorlens.infrastructure.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Sanitização de headers, corpos e textos livres antes de qualquer persistência ou exposição.
 * Oculta credenciais, tokens, cookies, senhas e documentos pessoais.
 */
public final class SensitiveDataSanitizer {

    public static final String REDACTED = "[REDACTED]";
    public static final int DEFAULT_BODY_LIMIT = 16 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key",
            "x-auth-token", "x-lab-control-token", "x-csrf-token", "x-xsrf-token");

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|passwd|senha|secret|token|authorization|apikey|api_key|credential|cpf|document|card|cvv).*");

    private static final List<Replacement> TEXT_RULES = List.of(
            new Replacement(Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{6,}"), "$1" + REDACTED),
            new Replacement(Pattern.compile("(?i)(Basic\\s+)[A-Za-z0-9+/=]{6,}"), "$1" + REDACTED),
            new Replacement(Pattern.compile(
                    "(?i)\\b(password|passwd|senha|secret|token|api[_-]?key)(\\s*[=:]\\s*)(\"[^\"]*\"|[^\\s,;&]+)"),
                    "$1$2" + REDACTED),
            new Replacement(Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b"), "***.***.***-**"),
            // Detalhe de violação de constraint do PostgreSQL: "Key (email)=(valor)" -> oculta o valor
            new Replacement(Pattern.compile("(Key \\([^)]*\\)=)\\(([^)]*)\\)"), "$1(" + REDACTED + ")"));

    private SensitiveDataSanitizer() {
    }

    public static Map<String, String> headers(Map<String, ? extends List<String>> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (headers == null) {
            return result;
        }
        headers.forEach((name, values) -> {
            String joined = values == null ? "" : String.join(", ", values);
            result.put(name, header(name, joined));
        });
        return result;
    }

    public static String header(String name, String value) {
        if (name == null || value == null) {
            return value;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (SENSITIVE_HEADERS.contains(lower)) {
            int space = value.indexOf(' ');
            if (lower.endsWith("authorization") && space > 0) {
                return value.substring(0, space) + " " + REDACTED;
            }
            return REDACTED;
        }
        return text(value, 2048);
    }

    /**
     * Sanitiza um corpo HTTP: se for JSON, mascara chaves sensíveis estruturalmente; caso contrário
     * aplica as regras textuais. O resultado é truncado em {@code limit} caracteres.
     */
    public static String body(String body, int limit) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                maskJson(node);
                return truncate(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node), limit);
            } catch (RuntimeException notJson) {
                // corpo malformado: mantém o texto original (sanitizado) para fins de diagnóstico
            }
        }
        return text(body, limit);
    }

    public static String text(String value, int limit) {
        if (value == null) {
            return null;
        }
        String result = value;
        for (Replacement rule : TEXT_RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return truncate(result, limit);
    }

    public static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "\n... [truncado em " + limit + " caracteres]";
    }

    private static void maskJson(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (String field : List.copyOf(object.propertyNames())) {
                JsonNode child = object.get(field);
                if (SENSITIVE_KEY.matcher(field).matches() && child != null && child.isValueNode()) {
                    object.put(field, REDACTED);
                } else {
                    maskJson(child);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) {
                maskJson(child);
            }
        }
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
