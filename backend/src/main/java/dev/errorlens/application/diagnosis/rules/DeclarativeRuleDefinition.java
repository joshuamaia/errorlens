package dev.errorlens.application.diagnosis.rules;

import java.util.List;

/**
 * Estrutura de uma regra declarada em {@code classpath:diagnosis/rules.json}.
 */
public record DeclarativeRuleDefinition(
        String id,
        String title,
        String category,
        String description,
        Match match,
        List<String> possibleCauses,
        List<String> suggestions,
        List<String> fixes,
        String codeExample) {

    /**
     * Critérios de correspondência. {@code exceptions} aceita nome simples ou totalmente qualificado.
     * Sem exception correspondente, a regra só dispara se {@code allowWithoutException} for verdadeiro.
     * Com {@code requireMessageMatch}, a regra exige um padrão de mensagem ou SQLState correspondente.
     */
    public record Match(
            List<String> exceptions,
            List<String> messagePatterns,
            List<Integer> httpStatuses,
            List<String> errorCodes,
            List<String> sqlStates,
            Boolean allowWithoutException,
            Boolean requireMessageMatch) {

        public Match {
            exceptions = exceptions == null ? List.of() : exceptions;
            messagePatterns = messagePatterns == null ? List.of() : messagePatterns;
            httpStatuses = httpStatuses == null ? List.of() : httpStatuses;
            errorCodes = errorCodes == null ? List.of() : errorCodes;
            sqlStates = sqlStates == null ? List.of() : sqlStates;
            allowWithoutException = Boolean.TRUE.equals(allowWithoutException);
            requireMessageMatch = Boolean.TRUE.equals(requireMessageMatch);
        }
    }
}
