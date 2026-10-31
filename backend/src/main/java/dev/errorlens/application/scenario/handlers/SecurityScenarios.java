package dev.errorlens.application.scenario.handlers;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.errorlens.application.scenario.ScenarioHandler;
import dev.errorlens.application.scenario.ScenarioHandlerGroup;
import dev.errorlens.application.scenario.ScenarioPlan;
import dev.errorlens.domain.execution.ExecutionMode;
import dev.errorlens.infrastructure.security.LabIdentities;

/**
 * Cenários de segurança (C01-C03) com autenticação HTTP Basic real contra o Spring Security.
 */
@Component
public class SecurityScenarios implements ScenarioHandlerGroup {

    private static final String AUDIT_REPORT = "/api/v1/lab/secure/audit-report";

    @Override
    public List<ScenarioHandler> handlers() {
        return List.of(
                ScenarioHandler.of("C01", (mode, s) -> mode == ExecutionMode.SUCCESS ? authorized() : anonymous()),
                ScenarioHandler.of("C02", (mode, s) -> mode == ExecutionMode.SUCCESS ? authorized() : forbidden()),
                ScenarioHandler.of("C03", (mode, s) -> mode == ExecutionMode.FAILURE ? forbidden() : authorized()));
    }

    private static ScenarioPlan anonymous() {
        return ScenarioPlan.request("GET", AUDIT_REPORT).expect(401)
                .variant("Requisição sem header Authorization").build();
    }

    private static ScenarioPlan forbidden() {
        return ScenarioPlan.request("GET", AUDIT_REPORT).as(LabIdentities.VIEWER).expect(403)
                .variant("Identidade 'viewer' (ROLE_VIEWER) sem ROLE_AUDITOR").build();
    }

    private static ScenarioPlan authorized() {
        return ScenarioPlan.request("GET", AUDIT_REPORT).as(LabIdentities.AUDITOR).expect(200)
                .variant("Identidade 'auditor' com ROLE_AUDITOR").build();
    }
}
