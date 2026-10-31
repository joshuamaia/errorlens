package dev.errorlens.application.diagnosis.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.errorlens.application.diagnosis.DiagnosisContext;
import dev.errorlens.application.diagnosis.DiagnosisContext.AttemptInfo;
import dev.errorlens.application.diagnosis.DiagnosisContext.StepInfo;
import dev.errorlens.application.diagnosis.DiagnosisResult;
import dev.errorlens.application.diagnosis.DiagnosisResult.Confidence;
import dev.errorlens.application.diagnosis.DiagnosisRule;

/**
 * Regras programáticas que dependem da sequência de eventos (tentativas, transições de
 * circuito, fallback, transações) e não apenas da exception final.
 */
@Configuration
public class ResilienceRules {

    @Bean
    public DiagnosisRule retryExhaustedRule() {
        return rule("RETRY_EXHAUSTED", context -> {
            List<AttemptInfo> attempts = context.attempts();
            long failures = attempts.stream().filter(a -> "FAILURE".equals(a.outcome())).count();
            boolean exhausted = hasStep(context, "Retry · esgotado");
            if (!exhausted || failures < 2) {
                return Optional.empty();
            }
            List<String> evidences = new ArrayList<>();
            evidences.add(failures + " tentativas registradas, todas com falha");
            attempts.stream().map(a -> "Tentativa " + a.number() + ": " + a.outcome()
                    + (a.httpStatus() == null ? "" : " (HTTP " + a.httpStatus() + ")")).forEach(evidences::add);
            evidences.add("Evento 'Retry · esgotado' publicado pelo Resilience4j");
            return Optional.of(result("RETRY_EXHAUSTED", "Tentativas de retry esgotadas", "RESILIENCIA",
                    "A dependência falhou em todas as tentativas permitidas pela política de retry. O erro final "
                            + "é a última falha observada.",
                    List.of("Indisponibilidade prolongada da dependência", "Erro não transitório sendo retentado",
                            "Política de retry curta demais para a janela de recuperação"),
                    evidences,
                    List.of("Verifique a saúde da dependência no período", "Confirme se o erro é realmente transitório",
                            "Consulte os intervalos entre tentativas na timeline"),
                    List.of("Combinar retry com circuit breaker para evitar sobrecarga",
                            "Retentar apenas erros transitórios (5xx/timeout), nunca 4xx",
                            "Oferecer fallback quando houver resposta alternativa aceitável"),
                    "resilience4j.retry.instances.customerProfile:\n  max-attempts: 4\n  wait-duration: 200ms\n"
                            + "  enable-exponential-backoff: true",
                    85));
        });
    }

    @Bean
    public DiagnosisRule retryRecoveredRule() {
        return rule("RETRY_RECOVERED", context -> {
            List<AttemptInfo> attempts = context.attempts();
            long failures = attempts.stream().filter(a -> "FAILURE".equals(a.outcome())).count();
            boolean recovered = hasStep(context, "Retry · recuperado")
                    && !attempts.isEmpty() && "SUCCESS".equals(attempts.getLast().outcome());
            if (!recovered || failures == 0) {
                return Optional.empty();
            }
            return Optional.of(result("RETRY_RECOVERED", "Falha transitória recuperada por retry", "RESILIENCIA",
                    "A operação concluiu com sucesso após " + failures + " falha(s) transitória(s). O resultado é "
                            + "correto, mas a latência total aumentou devido às tentativas.",
                    List.of("Instabilidade momentânea da dependência", "Sobrecarga temporária no serviço remoto"),
                    List.of(failures + " tentativa(s) com falha seguida(s) de sucesso",
                            "Evento 'Retry · recuperado' publicado pelo Resilience4j"),
                    List.of("Monitore a frequência de retries: aumentos indicam degradação",
                            "Compare a duração total com a de uma execução sem falhas"),
                    List.of("Garantir idempotência da operação retentada", "Expor métricas de retry (Micrometer)"),
                    null, 60));
        });
    }

    @Bean
    public DiagnosisRule circuitOpenRule() {
        return rule("CIRCUIT_BREAKER_OPEN", context -> {
            boolean rejected = context.exceptions().stream()
                    .anyMatch(e -> "CallNotPermittedException".equals(e.simpleName()))
                    || hasStep(context, "Circuit Breaker · chamada rejeitada");
            if (!rejected) {
                return Optional.empty();
            }
            List<String> evidences = new ArrayList<>();
            context.steps().stream()
                    .filter(s -> s.name().startsWith("Circuit Breaker ·"))
                    .map(s -> s.name() + (s.detail() == null ? "" : " — " + s.detail()))
                    .forEach(evidences::add);
            long rejectedCalls = context.attempts().stream().filter(a -> "REJECTED".equals(a.outcome())).count();
            evidences.add(rejectedCalls + " chamada(s) rejeitada(s) sem atingir a dependência");
            int score = context.exceptions().stream()
                    .anyMatch(e -> "CallNotPermittedException".equals(e.simpleName())) ? 90 : 60;
            return Optional.of(result("CIRCUIT_BREAKER_OPEN", "Circuit Breaker aberto", "RESILIENCIA",
                    "O circuito registrou uma taxa de falhas acima do limiar e passou a rejeitar chamadas "
                            + "imediatamente (CallNotPermittedException), protegendo a aplicação e a dependência.",
                    List.of("Sequência de falhas recentes na dependência",
                            "Chamadas de teste em HALF_OPEN também falharam",
                            "Limiar de falhas ou janela deslizante muito sensíveis"),
                    evidences,
                    List.of("Analise a sequência de estados na timeline (CLOSED → OPEN → HALF_OPEN)",
                            "Verifique a saúde da dependência durante o intervalo em OPEN",
                            "Consulte as métricas do circuito em /actuator/circuitbreakers"),
                    List.of("Oferecer fallback enquanto o circuito estiver aberto",
                            "Calibrar failureRateThreshold, slidingWindowSize e waitDurationInOpenState"),
                    "CircuitBreaker cb = registry.circuitBreaker(\"paymentGateway\");\n"
                            + "try {\n    return cb.executeSupplier(() -> client.authorize());\n"
                            + "} catch (CallNotPermittedException ex) {\n    return PaymentResult.pendente();\n}",
                    score));
        });
    }

    @Bean
    public DiagnosisRule fallbackRule() {
        return rule("FALLBACK_DEGRADED", context -> {
            boolean degraded = "DEGRADED".equals(context.outcome())
                    || "true".equalsIgnoreCase(context.responseHeaders().getOrDefault("X-ErrorLens-Degraded", ""))
                    || hasStep(context, "Fallback · resposta degradada");
            if (!degraded) {
                return Optional.empty();
            }
            List<String> evidences = new ArrayList<>();
            if (context.httpStatus() != null) {
                evidences.add("HTTP " + context.httpStatus() + " retornado, mas com resposta alternativa");
            }
            context.steps().stream().filter(s -> s.name().startsWith("Fallback"))
                    .map(s -> s.name() + " — " + s.detail()).forEach(evidences::add);
            context.attempts().stream().filter(a -> !"SUCCESS".equals(a.outcome()))
                    .map(a -> "Chamada à dependência: " + a.outcome()
                            + (a.httpStatus() == null ? "" : " (HTTP " + a.httpStatus() + ")"))
                    .forEach(evidences::add);
            return Optional.of(result("FALLBACK_DEGRADED", "Resposta degradada por fallback", "RESILIENCIA",
                    "A requisição foi atendida com uma resposta alternativa porque a dependência falhou. "
                            + "O status HTTP é de sucesso, mas o conteúdo não é o resultado normal.",
                    List.of("Dependência indisponível ou retornando erro", "Circuito aberto para a dependência"),
                    evidences,
                    List.of("Verifique a causa da falha da dependência na aba de tentativas",
                            "Garanta que consumidores conseguem distinguir respostas degradadas"),
                    List.of("Sinalizar degradação explicitamente (campo degraded / header)",
                            "Monitorar a taxa de respostas degradadas como indicador de saúde"),
                    null, 75));
        });
    }

    @Bean
    public DiagnosisRule transactionRollbackRule() {
        return rule("TRANSACTION_ROLLBACK", context -> {
            boolean rollback = hasStep(context, "PostgreSQL · ROLLBACK");
            List<StepInfo> writes = context.steps().stream()
                    .filter(s -> "DATABASE".equals(s.layer()) && "OK".equals(s.status())
                            && (s.name().contains("INSERT") || s.name().contains("UPDATE")
                            || s.name().contains("DELETE")))
                    .toList();
            if (!rollback || writes.isEmpty() || context.exceptions().isEmpty()) {
                return Optional.empty();
            }
            List<String> evidences = new ArrayList<>();
            writes.forEach(s -> evidences.add("Comando executado antes da falha: " + s.name()));
            evidences.add("Evento ROLLBACK registrado na conexão JDBC");
            evidences.add("Exception que interrompeu a transação: " + context.primary().simpleName());
            return Optional.of(result("TRANSACTION_ROLLBACK", "Alterações revertidas por rollback", "BANCO_DE_DADOS",
                    "Comandos de escrita chegaram ao banco, mas a transação foi revertida após a falha. "
                            + "Nenhuma alteração parcial deveria permanecer persistida.",
                    List.of("Exception de runtime lançada dentro do método @Transactional"),
                    evidences,
                    List.of("Confira a verificação automática de rollback desta execução",
                            "Revise a ordem das operações dentro da transação"),
                    List.of("Validar pré-condições antes de gravar",
                            "Usar exceptions de runtime para regras que devem reverter a transação"),
                    null, 70));
        });
    }

    @Bean
    public DiagnosisRule slowExecutionRule() {
        return rule("SLOW_EXECUTION", context -> {
            if (context.durationMs() == null || context.durationMs() < 2000 || !"SUCCESS".equals(context.outcome())) {
                return Optional.empty();
            }
            return Optional.of(result("SLOW_EXECUTION", "Execução lenta", "DESEMPENHO",
                    "A execução terminou com sucesso, porém levou " + context.durationMs() + " ms.",
                    List.of("Dependência lenta", "Retries com espera", "Consultas pesadas"),
                    List.of("Duração total de " + context.durationMs() + " ms"),
                    List.of("Identifique na timeline a etapa com maior duração"),
                    List.of("Otimizar a etapa dominante ou paralelizar chamadas independentes"),
                    null, 40));
        });
    }

    private static boolean hasStep(DiagnosisContext context, String name) {
        return context.steps().stream().anyMatch(s -> s.name().equals(name));
    }

    private static DiagnosisResult result(String id, String title, String category, String description,
                                          List<String> causes, List<String> evidences, List<String> suggestions,
                                          List<String> fixes, String code, int score) {
        return new DiagnosisResult(id, title, category, description, causes, evidences, suggestions, fixes, code,
                Confidence.fromScore(score), score);
    }

    private static DiagnosisRule rule(String id, java.util.function.Function<DiagnosisContext,
            Optional<DiagnosisResult>> evaluator) {
        return new DiagnosisRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Optional<DiagnosisResult> evaluate(DiagnosisContext context) {
                return evaluator.apply(context);
            }
        };
    }
}
