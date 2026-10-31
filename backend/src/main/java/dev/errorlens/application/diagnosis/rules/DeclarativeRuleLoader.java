package dev.errorlens.application.diagnosis.rules;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Carrega as regras declarativas de {@code classpath:diagnosis/rules.json}.
 */
@Component
public class DeclarativeRuleLoader {

    static final String LOCATION = "diagnosis/rules.json";

    private final JsonMapper jsonMapper;

    public DeclarativeRuleLoader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public List<DeclarativeDiagnosisRule> load() {
        try (InputStream in = new ClassPathResource(LOCATION).getInputStream()) {
            List<DeclarativeRuleDefinition> definitions = jsonMapper.readValue(in, new TypeReference<>() {
            });
            return definitions.stream().map(DeclarativeDiagnosisRule::new).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível carregar " + LOCATION, ex);
        }
    }
}
