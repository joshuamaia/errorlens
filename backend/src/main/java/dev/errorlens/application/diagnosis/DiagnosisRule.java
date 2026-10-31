package dev.errorlens.application.diagnosis;

import java.util.Optional;

/**
 * Regra local e determinística do Diagnosis Engine. Para adicionar uma regra, crie um bean
 * que implemente esta interface ou acrescente uma entrada em {@code diagnosis/rules.json}.
 */
public interface DiagnosisRule {

    String id();

    Optional<DiagnosisResult> evaluate(DiagnosisContext context);
}
