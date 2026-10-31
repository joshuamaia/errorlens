package dev.errorlens.application.diagnosis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.errorlens.application.diagnosis.rules.DeclarativeRuleLoader;

/**
 * Motor de diagnóstico baseado em regras locais (sem IA). Avalia regras declarativas (JSON) e
 * programáticas (beans), ordena as hipóteses pela pontuação de evidências e retorna as mais
 * relevantes.
 */
@Service
public class DiagnosisEngine {

    public static final String DISCLAIMER = "Os diagnósticos são hipóteses geradas por regras determinísticas "
            + "a partir das evidências coletadas. Confirme a causa analisando o código, os dados e o ambiente.";
    private static final Logger log = LoggerFactory.getLogger(DiagnosisEngine.class);
    private static final int MAX_RESULTS = 5;

    private final List<DiagnosisRule> rules;

    public DiagnosisEngine(List<DiagnosisRule> programmaticRules, DeclarativeRuleLoader loader) {
        List<DiagnosisRule> all = new ArrayList<>(programmaticRules);
        all.addAll(loader.load());
        this.rules = List.copyOf(all);
        log.info("Diagnosis Engine inicializado com {} regras", rules.size());
    }

    public List<DiagnosisResult> diagnose(DiagnosisContext context) {
        Map<String, DiagnosisResult> byRule = new LinkedHashMap<>();
        for (DiagnosisRule rule : rules) {
            try {
                Optional<DiagnosisResult> result = rule.evaluate(context);
                result.ifPresent(r -> byRule.merge(r.ruleId(), r, (a, b) -> a.score() >= b.score() ? a : b));
            } catch (RuntimeException ex) {
                log.warn("Regra de diagnóstico {} falhou e foi ignorada: {}", rule.id(), ex.getMessage());
            }
        }
        return byRule.values().stream()
                .sorted(Comparator.comparingInt(DiagnosisResult::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    public int ruleCount() {
        return rules.size();
    }

    public List<String> ruleIds() {
        return rules.stream().map(DiagnosisRule::id).toList();
    }
}
