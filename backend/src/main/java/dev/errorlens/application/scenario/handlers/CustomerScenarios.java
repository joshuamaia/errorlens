package dev.errorlens.application.scenario.handlers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.errorlens.application.scenario.ScenarioHandler;
import dev.errorlens.application.scenario.ScenarioHandlerGroup;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.application.scenario.ScenarioSupport;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.infrastructure.security.LabIdentities;

/**
 * Cenários de CRUD de clientes: sucesso (A01-A05), HTTP/validação (B01-B05) e integridade (D01-D02).
 */
@Component
public class CustomerScenarios implements ScenarioHandlerGroup {

    private static final String CUSTOMERS = "/api/v1/lab/customers";

    @Override
    public List<ScenarioHandler> handlers() {
        return List.of(
                ScenarioHandler.of("A01", (mode, s) -> mode == ExecutionMode.FAILURE ? findMissing() : findExisting(s)),
                ScenarioHandler.of("A02", (mode, s) -> mode == ExecutionMode.FAILURE ? createDuplicated(s)
                        : createValid(s)),
                ScenarioHandler.single("A03", (mode, s) -> update(s)),
                ScenarioHandler.of("A04", (mode, s) -> mode == ExecutionMode.FAILURE ? deleteWithOrders(s)
                        : deleteDisposable(s)),
                ScenarioHandler.single("A05", (mode, s) -> ScenarioPlan
                        .request("GET", CUSTOMERS + "?page=0&size=5&sort=name").as(LabIdentities.RUNNER)
                        .expect(200).variant("Primeira página com 5 clientes ordenados por nome").build()),

                ScenarioHandler.of("B01", (mode, s) -> mode == ExecutionMode.SUCCESS ? findExisting(s) : findMissing()),
                ScenarioHandler.of("B02", (mode, s) -> mode == ExecutionMode.SUCCESS ? createValid(s) : invalidDto()),
                ScenarioHandler.of("B03", (mode, s) -> mode == ExecutionMode.SUCCESS ? createValid(s) : malformedJson()),
                ScenarioHandler.single("B04", (mode, s) -> ScenarioPlan.request("GET", CUSTOMERS + "/abc")
                        .as(LabIdentities.RUNNER).expect(400)
                        .variant("Path variable 'abc' enviado para um parâmetro Long").build()),
                ScenarioHandler.single("B05", (mode, s) -> ScenarioPlan.request("PATCH", CUSTOMERS)
                        .as(LabIdentities.RUNNER).body("{\"name\":\"Atualização parcial\"}").expect(405)
                        .variant("PATCH na coleção, que aceita apenas GET e POST").build()),

                ScenarioHandler.of("D01", (mode, s) -> mode == ExecutionMode.SUCCESS ? createValid(s)
                        : createDuplicated(s)),
                ScenarioHandler.of("D02", (mode, s) -> mode == ExecutionMode.SUCCESS ? deleteDisposable(s)
                        : deleteWithOrders(s)));
    }

    private static ScenarioPlan findExisting(ScenarioSupport s) {
        long id = s.seedCustomerId(ScenarioSupport.SEED_ANA);
        return ScenarioPlan.request("GET", CUSTOMERS + "/" + id).as(LabIdentities.RUNNER).expect(200)
                .variant("Cliente semeado existente (id " + id + ")").build();
    }

    private static ScenarioPlan findMissing() {
        return ScenarioPlan.request("GET", CUSTOMERS + "/" + ScenarioSupport.MISSING_ID).as(LabIdentities.RUNNER)
                .expect(404).variant("Identificador inexistente " + ScenarioSupport.MISSING_ID).build();
    }

    private static ScenarioPlan createValid(ScenarioSupport s) {
        String email = s.uniqueEmail("cliente");
        return ScenarioPlan.request("POST", CUSTOMERS).as(LabIdentities.RUNNER)
                .body(s.json(customer("Cliente Lab " + s.shortId(), email, "123.456.789-09", "1500.00")))
                .expect(201).variant("E-mail único gerado para a execução: " + email).build();
    }

    private static ScenarioPlan createDuplicated(ScenarioSupport s) {
        return ScenarioPlan.request("POST", CUSTOMERS).as(LabIdentities.RUNNER)
                .body(s.json(customer("Ana Souza (duplicada)", ScenarioSupport.SEED_ANA, "111.444.777-35", "1000.00")))
                .expect(409).variant("E-mail já cadastrado: " + ScenarioSupport.SEED_ANA).build();
    }

    private static ScenarioPlan update(ScenarioSupport s) {
        long id = s.seedCustomerId(ScenarioSupport.SEED_BRUNO);
        BigDecimal current = s.jdbc().queryForObject("SELECT credit_limit FROM customers WHERE id = ?",
                BigDecimal.class, id);
        // alterna o limite para garantir um UPDATE real a cada execução (dirty checking)
        String next = current != null && current.compareTo(new BigDecimal("8000.00")) == 0 ? "8500.00" : "8000.00";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Bruno Lima");
        body.put("status", "ACTIVE");
        body.put("creditLimit", new BigDecimal(next));
        return ScenarioPlan.request("PUT", CUSTOMERS + "/" + id).as(LabIdentities.RUNNER).body(s.json(body))
                .expect(200).variant("Limite de crédito alterado de " + current + " para " + next).build();
    }

    private static ScenarioPlan deleteDisposable(ScenarioSupport s) {
        long id = s.createDisposableCustomer(s.shortId(), new BigDecimal("100.00"));
        return ScenarioPlan.request("DELETE", CUSTOMERS + "/" + id).as(LabIdentities.RUNNER).expect(204)
                .variant("Cliente descartável id " + id + " criado na preparação, sem pedidos").build();
    }

    private static ScenarioPlan deleteWithOrders(ScenarioSupport s) {
        long id = s.seedCustomerId(ScenarioSupport.SEED_ANA);
        return ScenarioPlan.request("DELETE", CUSTOMERS + "/" + id).as(LabIdentities.RUNNER).expect(409)
                .variant("Cliente id " + id + " possui pedidos (fk_orders_customer)").build();
    }

    private static ScenarioPlan invalidDto() {
        return ScenarioPlan.request("POST", CUSTOMERS).as(LabIdentities.RUNNER)
                .body("{\n  \"name\": \"\",\n  \"email\": \"email-invalido\",\n  \"document\": \"123\",\n"
                        + "  \"creditLimit\": -10\n}")
                .expect(400).variant("Nome vazio, e-mail inválido, documento fora do formato e limite negativo")
                .build();
    }

    private static ScenarioPlan malformedJson() {
        return ScenarioPlan.request("POST", CUSTOMERS).as(LabIdentities.RUNNER)
                .body("{\n  \"name\": \"Cliente\",\n  \"email\": \"cliente@lab.errorlens.dev\",,\n"
                        + "  \"creditLimit\": 100\n")
                .expect(400).variant("Vírgula duplicada e chave de fechamento ausente").build();
    }

    private static Map<String, Object> customer(String name, String email, String document, String credit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("document", document);
        body.put("creditLimit", new BigDecimal(credit));
        return body;
    }
}
