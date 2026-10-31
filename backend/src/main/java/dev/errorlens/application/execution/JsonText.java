package dev.errorlens.application.execution;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conversão entre objetos e as colunas TEXT que armazenam JSON (listas, mapas de headers).
 */
@Component
public class JsonText {

    private final JsonMapper mapper;

    public JsonText(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    public List<String> stringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JacksonException ex) {
            return List.of(json);
        }
    }

    public Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException ex) {
            return Map.of();
        }
    }

    public List<Map<String, Object>> mapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JacksonException ex) {
            return List.of();
        }
    }

    /**
     * Interpreta um corpo HTTP como JSON quando possível (para exibição/diff), senão retorna null.
     */
    public Object tryParse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return mapper.readValue(trimmed, Object.class);
        } catch (JacksonException ex) {
            return null;
        }
    }
}
