package dev.errorlens.application.scenario.handlers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.errorlens.application.lab.InventoryService;
import dev.errorlens.application.scenario.ScenarioHandler;
import dev.errorlens.application.scenario.ScenarioHandlerGroup;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.application.scenario.ScenarioSupport;
import dev.errorlens.application.scenario.Verification;
import dev.errorlens.application.scenario.Verification.Check;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.infrastructure.security.LabIdentities;

/**
 * Cenários de persistência (D03, D04) e de exceptions Java (E01-E03).
 */
@Component
public class DataAndExceptionScenarios implements ScenarioHandlerGroup {

    @Override
    public List<ScenarioHandler> handlers() {
        return List.of(
                ScenarioHandler.single("D03", (mode, s) -> archive(s)),
                ScenarioHandler.of("D04", (mode, s) -> checkout(s, mode != ExecutionMode.SUCCESS)),
                ScenarioHandler.of("E01", (mode, s) -> loyalty(s, mode != ExecutionMode.SUCCESS)),
                ScenarioHandler.of("E02", (mode, s) -> discount(mode == ExecutionMode.SUCCESS ? 15 : 150)),
                ScenarioHandler.of("E03", (mode, s) -> invoice(s, mode != ExecutionMode.SUCCESS)));
    }

    private static ScenarioPlan archive(ScenarioSupport s) {
        long id = s.seedCustomerId(ScenarioSupport.SEED_CARLA);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", id);
        body.put("reason", "Encerramento de conta solicitado pelo cliente");
        return ScenarioPlan.request("POST", "/api/v1/lab/persistence/archive").as(LabIdentities.RUNNER)
                .body(s.json(body)).expect(500)
                .variant("Gravação na tabela customer_archive (não criada por nenhuma migration)").build();
    }

    /**
     * Cria um cliente descartável, registra o estado antes da execução e verifica no banco,
     * após a resposta, se as alterações foram revertidas (falha) ou confirmadas (sucesso).
     */
    private static ScenarioPlan checkout(ScenarioSupport s, boolean failInventory) {
        BigDecimal initialCredit = new BigDecimal("5000.00");
        BigDecimal amount = new BigDecimal("1299.90");
        long customerId = s.createDisposableCustomer("checkout " + s.shortId(), initialCredit);
        long versionBefore = s.jdbc().queryForObject("SELECT version FROM customers WHERE id = ?", Long.class,
                customerId);
        String description = "Checkout D04 " + s.shortId();
        String sku = failInventory ? InventoryService.SKU_DISCONTINUED : InventoryService.SKU_AVAILABLE;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("sku", sku);
        body.put("quantity", 1);
        body.put("amount", amount);
        body.put("description", description);

        return ScenarioPlan.request("POST", "/api/v1/lab/orders/checkout").as(LabIdentities.RUNNER)
                .body(s.json(body))
                .expect(failInventory ? 500 : 201)
                .variant(failInventory
                        ? "SKU descontinuado (sem estoque): a reserva falha após INSERT/UPDATE"
                        : "SKU com estoque: pedido e débito de crédito confirmados")
                .verify(() -> {
                    long orders = s.jdbc().queryForObject(
                            "SELECT count(*) FROM orders WHERE customer_id = ? AND description = ?", Long.class,
                            customerId, description);
                    BigDecimal credit = s.jdbc().queryForObject(
                            "SELECT credit_limit FROM customers WHERE id = ?", BigDecimal.class, customerId);
                    long version = s.jdbc().queryForObject("SELECT version FROM customers WHERE id = ?",
                            Long.class, customerId);
                    if (failInventory) {
                        return Verification.of("Verificação de rollback", "ROLLBACK_CONFIRMADO",
                                "ALTERACAO_PARCIAL_DETECTADA", List.of(
                                        Check.of("Pedidos persistidos para o cliente", 0, orders),
                                        Check.of("Limite de crédito do cliente", initialCredit, credit),
                                        Check.of("Versão do registro do cliente", versionBefore, version)));
                    }
                    return Verification.of("Verificação de commit", "COMMIT_CONFIRMADO", "COMMIT_INCOMPLETO",
                            List.of(Check.of("Pedidos persistidos para o cliente", 1, orders),
                                    Check.of("Limite de crédito do cliente", initialCredit.subtract(amount), credit),
                                    Check.of("Versão do registro do cliente", versionBefore + 1, version)));
                })
                .build();
    }

    private static ScenarioPlan loyalty(ScenarioSupport s, boolean withoutTier) {
        String email = withoutTier ? ScenarioSupport.SEED_CARLA : ScenarioSupport.SEED_ANA;
        long id = s.seedCustomerId(email);
        return ScenarioPlan.request("GET", "/api/v1/lab/customers/" + id + "/loyalty").as(LabIdentities.RUNNER)
                .expect(withoutTier ? 500 : 200)
                .variant(withoutTier ? "Cliente id " + id + " sem tier de fidelidade (perfil null)"
                        : "Cliente id " + id + " com tier GOLD")
                .build();
    }

    private static ScenarioPlan discount(int percentage) {
        return ScenarioPlan.request("GET", "/api/v1/lab/orders/discount-simulation?amount=1000.00&percentage="
                        + percentage).as(LabIdentities.RUNNER)
                .expect(percentage <= 100 ? 200 : 400)
                .variant("Percentual de desconto " + percentage + "%").build();
    }

    private static ScenarioPlan invoice(ScenarioSupport s, boolean corrupted) {
        long orderId = s.orderIdWithMetadata(corrupted ? "parcelas=três" : "parcelas=10");
        return ScenarioPlan.request("POST", "/api/v1/lab/orders/" + orderId + "/invoice").as(LabIdentities.RUNNER)
                .expect(corrupted ? 422 : 200)
                .variant(corrupted ? "Pedido id " + orderId + " com metadado 'parcelas=três'"
                        : "Pedido id " + orderId + " com metadado 'parcelas=10'")
                .build();
    }
}
