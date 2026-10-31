package dev.errorlens.interfaces.lab;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.lab.AuditReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Recurso protegido por ROLE_AUDITOR (regra no SecurityFilterChain).
 */
@RestController
@RequestMapping("/api/v1/lab/secure")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Laboratório · Segurança", description = "Recurso protegido usado pelos cenários C01-C03")
public class LabSecureController {

    private final AuditReportService auditReportService;

    public LabSecureController(AuditReportService auditReportService) {
        this.auditReportService = auditReportService;
    }

    @GetMapping("/audit-report")
    @Operation(summary = "Relatório de auditoria (requer ROLE_AUDITOR)")
    public AuditReportService.AuditReport auditReport(Authentication authentication) {
        return auditReportService.generate(authentication.getName());
    }
}
