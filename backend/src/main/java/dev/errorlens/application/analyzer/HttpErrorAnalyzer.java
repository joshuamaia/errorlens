package dev.errorlens.application.analyzer;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.errorlens.application.diagnosis.DiagnosisContext;
import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisEngine;
import dev.errorlens.application.diagnosis.DiagnosisResult;
import dev.errorlens.domain.exception.InvalidInputException;
import dev.errorlens.infrastructure.observability.SensitiveDataSanitizer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Interpreta respostas HTTP de erro (Problem Details RFC 9457, erro padrão do Spring Boot e
 * estruturas JSON comuns) e aplica regras determinísticas. A URL informada é apenas
 * interpretada: nenhuma requisição é feita a ela.
 */
@Service
public class HttpErrorAnalyzer {

    static final int MAX_BODY = 256 * 1024;
    private static final Pattern EXCEPTION_NAME = Pattern.compile(
            "\\b((?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error))\\b");
    private static final Pattern STACK_FRAME = Pattern.compile("\\bat (?:[\\w$]+\\.)+[\\w$<>]+\\([^)]*\\)");

    private final JsonMapper mapper;
    private final DiagnosisEngine engine;

    public HttpErrorAnalyzer(JsonMapper mapper, DiagnosisEngine engine) {
        this.mapper = mapper;
        this.engine = engine;
    }

    public HttpErrorAnalysis analyze(HttpErrorInput input) {
        if (input.status() == null || input.status() < 100 || input.status() > 599) {
            throw new InvalidInputException("INVALID_STATUS", "Informe um status HTTP entre 100 e 599.");
        }
        String body = input.body() == null ? "" : input.body();
        if (body.length() > MAX_BODY) {
            throw new InvalidInputException(HttpStatus.CONTENT_TOO_LARGE, "CONTENT_TOO_LARGE",
                    "O corpo excede o limite de " + MAX_BODY + " caracteres.");
        }
        int status = input.status();
        Map<String, String> headers = parseHeaders(input.headers());
        RequestInfo request = request(input.method(), input.url());
        List<Finding> findings = new ArrayList<>();

        String format;
        Map<String, Object> extracted = new LinkedHashMap<>();
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        JsonNode json = parse(body);
        String contentType = header(headers, "content-type");
        if (body.isBlank()) {
            format = "EMPTY";
        } else if (json != null && json.isObject()) {
            if (json.has("title") && json.has("status") && (json.has("type") || json.has("detail"))) {
                format = "PROBLEM_DETAILS";
                copy(json, extracted, "type", "title", "status", "detail", "instance");
                if (contentType != null && !contentType.contains("application/problem+json")) {
                    findings.add(new Finding("INFO", "Problem Details com Content-Type genérico",
                            "O corpo segue o formato RFC 9457, mas o Content-Type é '" + contentType + "'.",
                            "Content-Type: " + contentType,
                            "Use application/problem+json para facilitar o tratamento automático pelos clientes."));
                }
            } else if (json.has("timestamp") && json.has("status") && json.has("error") && json.has("path")) {
                format = "SPRING_BOOT_ERROR";
                copy(json, extracted, "timestamp", "status", "error", "path", "message", "exception");
            } else {
                format = "GENERIC_JSON";
                copy(json, extracted, "error", "message", "code", "description", "error_description", "detail");
            }
            copy(json, extracted, "errorCode", "correlationId", "traceId", "timestamp", "exception");
            collectFieldErrors(json, fieldErrors);
            if (json.has("trace") || body.contains("\tat ") || STACK_FRAME.matcher(body).find()) {
                findings.add(new Finding("ERROR", "Stack trace exposto na resposta",
                        "A resposta pública contém frames de stack trace, revelando detalhes internos da aplicação.",
                        "Campo 'trace' ou frames 'at ...' presentes no corpo",
                        "Desabilite server.error.include-stacktrace e mantenha detalhes técnicos apenas em logs."));
            }
            Object bodyStatus = extracted.get("status");
            if (bodyStatus != null && !String.valueOf(bodyStatus).equals(String.valueOf(status))) {
                findings.add(new Finding("WARN", "Status do corpo diferente do status HTTP",
                        "O corpo informa status " + bodyStatus + ", mas a resposta HTTP é " + status + ".",
                        "status=" + bodyStatus, "Padronize o status no corpo e na linha de status da resposta."));
            }
        } else if (body.trim().startsWith("<")) {
            format = "HTML";
            findings.add(new Finding("WARN", "Resposta de erro em HTML",
                    "O corpo é HTML, o que costuma indicar página de erro de proxy, servidor web ou Whitelabel.",
                    abbreviate(body, 120),
                    "Verifique se a requisição chegou à aplicação ou foi respondida por um proxy/gateway."));
        } else {
            format = "TEXT";
        }
        if (!fieldErrors.isEmpty()) {
            extracted.put("errors", fieldErrors);
        }

        if (Set.of("password", "senha", "secret", "\"token\"").stream()
                .anyMatch(k -> body.toLowerCase(Locale.ROOT).contains(k))) {
            findings.add(new Finding("WARN", "Possível dado sensível no corpo",
                    "O corpo contém termos associados a credenciais.", "password/token/secret",
                    "Revise a resposta para garantir que segredos não são expostos."));
        }
        findings.addAll(statusFindings(status, headers, fieldErrors, body, request));

        Set<String> exceptionNames = new LinkedHashSet<>();
        if (extracted.get("exception") != null) {
            exceptionNames.add(String.valueOf(extracted.get("exception")));
        }
        Matcher matcher = EXCEPTION_NAME.matcher(body);
        while (matcher.find() && exceptionNames.size() < 5) {
            exceptionNames.add(matcher.group(1));
        }
        List<ExceptionInfo> exceptions = exceptionNames.stream()
                .map(name -> new ExceptionInfo(name, String.valueOf(extracted.getOrDefault("detail",
                        extracted.getOrDefault("message", "")))))
                .toList();
        String errorCode = extracted.get("errorCode") == null ? null : String.valueOf(extracted.get("errorCode"));
        List<DiagnosisResult> diagnoses = engine.diagnose(new DiagnosisContext(DiagnosisContext.Source.HTTP_ERROR,
                status, errorCode, exceptions, null,
                List.of(new DiagnosisContext.LogInfo("ERROR", abbreviate(body, 2000), null)), null, null, null,
                headers));

        HttpStatus resolved = HttpStatus.resolve(status);
        Map<String, String> sanitizedHeaders = new LinkedHashMap<>();
        headers.forEach((k, v) -> sanitizedHeaders.put(k, SensitiveDataSanitizer.header(k, v)));
        return new HttpErrorAnalysis(request, status, resolved == null ? "Status não padronizado"
                : resolved.getReasonPhrase(), statusClass(status), format, extracted, sanitizedHeaders, findings,
                diagnoses, DiagnosisEngine.DISCLAIMER);
    }

    private List<Finding> statusFindings(int status, Map<String, String> headers, List<Map<String, String>> fieldErrors,
                                         String body, RequestInfo request) {
        List<Finding> findings = new ArrayList<>();
        String lower = body.toLowerCase(Locale.ROOT);
        switch (status) {
            case 400 -> {
                if (!fieldErrors.isEmpty()) {
                    findings.add(new Finding("ERROR", "Falha de validação de campos",
                            fieldErrors.size() + " campo(s) violaram regras de validação.",
                            fieldErrors.stream().map(e -> e.get("field") + ": " + e.get("message")).toList().toString(),
                            "Corrija os campos indicados e confira o contrato do endpoint."));
                } else if (lower.contains("json") || lower.contains("parse") || lower.contains("malformed")
                        || lower.contains("not readable") || lower.contains("interpretado")) {
                    findings.add(new Finding("ERROR", "Corpo da requisição ilegível",
                            "O servidor não conseguiu interpretar o corpo enviado (sintaxe ou tipo).",
                            "Mensagem menciona parse/JSON", "Valide o JSON e os tipos dos campos."));
                } else {
                    findings.add(new Finding("ERROR", "Requisição inválida",
                            "O servidor rejeitou a requisição por parâmetros ou formato inválidos.", "HTTP 400",
                            "Compare parâmetros, headers e corpo com a documentação OpenAPI."));
                }
            }
            case 401 -> {
                String challenge = header(headers, "www-authenticate");
                findings.add(new Finding("ERROR", "Autenticação ausente ou inválida",
                        challenge == null ? "O recurso exige autenticação, mas a resposta não informa o esquema."
                                : "O servidor exige o esquema de autenticação: " + challenge,
                        challenge == null ? "Sem header WWW-Authenticate" : "WWW-Authenticate: " + challenge,
                        "Envie credenciais válidas no header Authorization ou renove o token."));
            }
            case 403 -> findings.add(new Finding("ERROR", "Acesso negado para a identidade autenticada",
                    "A identidade foi reconhecida, mas não possui permissão para o recurso.", "HTTP 403",
                    "Verifique roles/escopos exigidos pelo endpoint."));
            case 404 -> findings.add(lower.contains("no static resource") || lower.contains("no handler found")
                    ? new Finding("ERROR", "Rota não mapeada", "Nenhum endpoint corresponde ao caminho "
                    + request.path() + ".", "Mensagem indica ausência de handler",
                    "Confira o caminho, o prefixo da API e o método HTTP.")
                    : new Finding("WARN", "Recurso não encontrado",
                    "O endpoint existe, mas o recurso identificado não foi encontrado.", "HTTP 404",
                    "Confirme se o identificador existe."));
            case 405 -> {
                String allow = header(headers, "allow");
                findings.add(new Finding("ERROR", "Método HTTP não permitido",
                        "O método " + request.method() + " não é aceito por este recurso.",
                        allow == null ? "Header Allow ausente" : "Allow: " + allow,
                        allow == null ? "Consulte a documentação para os métodos suportados."
                                : "Use um dos métodos permitidos: " + allow));
            }
            case 406 -> findings.add(new Finding("ERROR", "Formato de resposta não aceitável",
                    "O servidor não produz o formato solicitado no header Accept.", "HTTP 406",
                    "Ajuste o header Accept (ex.: application/json)."));
            case 409 -> findings.add(new Finding("ERROR", "Conflito com o estado atual do recurso",
                    lower.contains("duplic") || lower.contains("já existe") || lower.contains("unique")
                            ? "Indício de duplicidade (registro já existente)."
                            : "A operação conflita com o estado atual (integridade, concorrência ou regra).",
                    "HTTP 409", "Verifique se o registro já existe ou se possui dependências."));
            case 413 -> findings.add(new Finding("ERROR", "Payload muito grande",
                    "O corpo excede o limite aceito pelo servidor.", "HTTP 413",
                    "Reduza o tamanho do envio ou utilize upload em partes."));
            case 415 -> findings.add(new Finding("ERROR", "Content-Type não suportado",
                    "O servidor não aceita o tipo de mídia enviado.", "HTTP 415",
                    "Envie Content-Type: application/json (ou o tipo documentado)."));
            case 422 -> findings.add(new Finding("ERROR", "Entidade não processável",
                    "A requisição é sintaticamente válida, mas viola regras de negócio.", "HTTP 422",
                    "Leia o detalhe do erro para identificar a regra violada."));
            case 429 -> {
                String retry = header(headers, "retry-after");
                findings.add(new Finding("WARN", "Limite de requisições atingido",
                        "O cliente excedeu a taxa permitida.", retry == null ? "HTTP 429" : "Retry-After: " + retry,
                        "Aplique backoff e respeite o Retry-After."));
            }
            case 500 -> findings.add(new Finding("ERROR", "Erro interno no servidor",
                    "Falha não tratada ou inesperada na aplicação.", "HTTP 500",
                    "Use o correlationId/traceId para localizar o stack trace nos logs do servidor."));
            case 502 -> findings.add(new Finding("ERROR", "Falha em dependência (Bad Gateway)",
                    "Um gateway ou serviço recebeu resposta inválida de uma dependência.", "HTTP 502",
                    "Investigue a dependência chamada e seus logs."));
            case 503 -> {
                String retry = header(headers, "retry-after");
                findings.add(new Finding("ERROR", "Serviço indisponível",
                        "O serviço está sobrecarregado, em manutenção ou com circuito aberto.",
                        retry == null ? "HTTP 503" : "Retry-After: " + retry,
                        "Verifique saúde do serviço e circuit breakers; tente novamente com backoff."));
            }
            case 504 -> findings.add(new Finding("ERROR", "Tempo limite no gateway",
                    "Uma dependência não respondeu dentro do tempo limite.", "HTTP 504",
                    "Compare a latência da dependência com os timeouts configurados."));
            default -> {
                if (status >= 400) {
                    findings.add(new Finding("WARN", "Status de erro " + status, "Status sem regra específica.",
                            "HTTP " + status, "Consulte a documentação do serviço."));
                }
            }
        }
        if (header(headers, "x-correlation-id") != null || header(headers, "traceparent") != null) {
            findings.add(new Finding("INFO", "Identificadores de rastreamento disponíveis",
                    "Use-os para localizar logs e traces correlacionados.",
                    "X-Correlation-Id: " + header(headers, "x-correlation-id"),
                    "Busque o correlationId no histórico do ErrorLens ou no Jaeger."));
        }
        return findings;
    }

    private JsonNode parse(String body) {
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return mapper.readTree(trimmed);
        } catch (JacksonException ex) {
            return null;
        }
    }

    private static void collectFieldErrors(JsonNode json, List<Map<String, String>> out) {
        for (String key : List.of("errors", "fieldErrors", "violations", "invalidParams", "invalid-params")) {
            JsonNode node = json.get(key);
            if (node != null && node.isArray()) {
                for (JsonNode item : node) {
                    Map<String, String> error = new LinkedHashMap<>();
                    error.put("field", text(item, "field", "name", "propertyPath", "param"));
                    error.put("message", text(item, "message", "defaultMessage", "reason", "detail"));
                    out.add(error);
                }
            }
        }
    }

    private static String text(JsonNode node, String... names) {
        if (node.isValueNode()) {
            return node.asString();
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asString();
            }
        }
        return null;
    }

    private static void copy(JsonNode json, Map<String, Object> out, String... fields) {
        for (String field : fields) {
            JsonNode value = json.get(field);
            if (value != null && !value.isNull() && !out.containsKey(field)) {
                out.put(field, value.isNumber() ? value.numberValue() : value.isValueNode() ? value.asString()
                        : value.toString());
            }
        }
    }

    static Map<String, String> parseHeaders(String raw) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (raw == null) {
            return headers;
        }
        for (String line : raw.split("\\R")) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                headers.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }
        return headers;
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }

    private static RequestInfo request(String method, String url) {
        String m = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        if (url == null || url.isBlank()) {
            return new RequestInfo(m, null, null, List.of());
        }
        try {
            URI uri = URI.create(url.trim());
            List<String> params = uri.getRawQuery() == null ? List.of()
                    : List.of(uri.getRawQuery().split("&")).stream().map(p -> p.split("=", 2)[0]).toList();
            return new RequestInfo(m, uri.getHost(), uri.getPath(), params);
        } catch (IllegalArgumentException ex) {
            return new RequestInfo(m, null, url.trim(), List.of());
        }
    }

    private static String statusClass(int status) {
        return switch (status / 100) {
            case 1 -> "1xx Informativo";
            case 2 -> "2xx Sucesso";
            case 3 -> "3xx Redirecionamento";
            case 4 -> "4xx Erro do cliente";
            default -> "5xx Erro do servidor";
        };
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public record HttpErrorInput(String method, String url, Integer status, String headers, String body) {
    }

    public record RequestInfo(String method, String host, String path, List<String> queryParameters) {
    }

    public record Finding(String severity, String title, String description, String evidence, String suggestion) {
    }

    public record HttpErrorAnalysis(RequestInfo request, int status, String reason, String statusClass,
                                    String format, Map<String, Object> extracted, Map<String, String> headers,
                                    List<Finding> findings, List<DiagnosisResult> diagnoses, String disclaimer) {
    }
}
