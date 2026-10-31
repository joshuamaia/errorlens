package dev.errorlens.application.diagnosis.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.errorlens.application.diagnosis.DiagnosisContext;
import dev.errorlens.application.diagnosis.DiagnosisContext.ExceptionInfo;
import dev.errorlens.application.diagnosis.DiagnosisResult;
import dev.errorlens.application.diagnosis.DiagnosisRule;

/**
 * Regra genérica orientada a dados. A pontuação soma o peso de cada evidência encontrada:
 * exception na root cause (50), exception principal (40) ou intermediária (30), SQLState (20),
 * padrão de mensagem (15), status HTTP (15) e errorCode (15). A pontuação máxima é 95, pois
 * regras nunca comprovam a causa.
 */
public class DeclarativeDiagnosisRule implements DiagnosisRule {

    private static final int MAX_SCORE = 95;

    private final DeclarativeRuleDefinition definition;
    private final List<Pattern> patterns;

    public DeclarativeDiagnosisRule(DeclarativeRuleDefinition definition) {
        this.definition = definition;
        this.patterns = definition.match().messagePatterns().stream()
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .toList();
    }

    @Override
    public String id() {
        return definition.id();
    }

    @Override
    public Optional<DiagnosisResult> evaluate(DiagnosisContext context) {
        DeclarativeRuleDefinition.Match match = definition.match();
        List<String> evidences = new ArrayList<>();
        int score = 0;

        int exceptionScore = exceptionScore(context, evidences);
        score += exceptionScore;

        boolean messageMatched = false;
        for (Pattern pattern : patterns) {
            Optional<String> hit = context.searchableTexts().map(pattern::matcher).filter(Matcher::find)
                    .map(Matcher::group).findFirst();
            if (hit.isPresent()) {
                score += 15;
                messageMatched = true;
                evidences.add("Mensagem contém \"" + abbreviate(hit.get()) + "\"");
                break;
            }
        }
        for (String sqlState : match.sqlStates()) {
            boolean found = context.searchableTexts().anyMatch(text -> text.contains(sqlState));
            if (found) {
                score += 20;
                messageMatched = true;
                evidences.add("SQLState " + sqlState + " presente nas mensagens do driver JDBC");
                break;
            }
        }
        boolean otherEvidence = score > 0;
        boolean statusMatched = false;
        if (context.httpStatus() != null && match.httpStatuses().contains(context.httpStatus())
                && (otherEvidence || match.allowWithoutException())) {
            score += 15;
            statusMatched = true;
            evidences.add("Status HTTP " + context.httpStatus() + " compatível com este padrão");
        }
        boolean errorCodeMatched = false;
        if (context.errorCode() != null && match.errorCodes().contains(context.errorCode())) {
            score += 15;
            errorCodeMatched = true;
            evidences.add("errorCode " + context.errorCode() + " associado a este padrão");
        }

        // Sem exception correspondente, exige evidência específica: errorCode ou status + mensagem.
        boolean fires = exceptionScore > 0 || (match.allowWithoutException()
                && (errorCodeMatched || (statusMatched && messageMatched)));
        if (!fires || (match.requireMessageMatch() && !messageMatched)) {
            return Optional.empty();
        }
        int finalScore = Math.min(score, MAX_SCORE);
        return Optional.of(new DiagnosisResult(definition.id(), definition.title(), definition.category(),
                definition.description(), safe(definition.possibleCauses()), evidences,
                safe(definition.suggestions()), safe(definition.fixes()), definition.codeExample(),
                DiagnosisResult.Confidence.fromScore(finalScore), finalScore));
    }

    private int exceptionScore(DiagnosisContext context, List<String> evidences) {
        List<ExceptionInfo> chain = context.exceptions();
        int best = 0;
        String evidence = null;
        for (int i = 0; i < chain.size(); i++) {
            ExceptionInfo info = chain.get(i);
            if (!matchesException(info)) {
                continue;
            }
            int candidate;
            String position;
            if (i == chain.size() - 1) {
                candidate = 50;
                position = chain.size() == 1 ? "exception registrada" : "root cause da cadeia";
            } else if (i == 0) {
                candidate = 40;
                position = "exception principal";
            } else {
                candidate = 30;
                position = "causa intermediária (nível " + (i + 1) + " da cadeia)";
            }
            if (candidate > best) {
                best = candidate;
                evidence = "Exception " + info.simpleName() + " encontrada como " + position;
            }
        }
        if (evidence != null) {
            evidences.add(evidence);
        }
        return best;
    }

    private boolean matchesException(ExceptionInfo info) {
        for (String expected : definition.match().exceptions()) {
            if (expected.contains(".") ? expected.equals(info.className()) : expected.equals(info.simpleName())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String abbreviate(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
