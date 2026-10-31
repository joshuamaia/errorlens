package dev.errorlens.application.diagnosis;

import java.util.List;

/**
 * Hipótese diagnóstica produzida por uma regra. A confiança reflete a força das evidências
 * encontradas; nunca representa uma causa comprovada.
 */
public record DiagnosisResult(
        String ruleId,
        String title,
        String category,
        String description,
        List<String> possibleCauses,
        List<String> evidences,
        List<String> suggestions,
        List<String> fixes,
        String codeExample,
        Confidence confidence,
        int score) {

    public enum Confidence {
        ALTA, MEDIA, BAIXA;

        public static Confidence fromScore(int score) {
            if (score >= 70) {
                return ALTA;
            }
            return score >= 45 ? MEDIA : BAIXA;
        }
    }
}
