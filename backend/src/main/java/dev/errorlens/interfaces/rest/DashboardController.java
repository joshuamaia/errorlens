package dev.errorlens.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.dashboard.DashboardService;
import dev.errorlens.application.health.ServiceHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard", description = "Indicadores e saúde dos serviços")
public class DashboardController {

    private final DashboardService dashboard;
    private final ServiceHealthService health;

    public DashboardController(DashboardService dashboard, ServiceHealthService health) {
        this.dashboard = dashboard;
        this.health = health;
    }

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Indicadores do período (1h, 24h, 7d, 30d)")
    public DashboardService.DashboardSummary summary(@RequestParam(defaultValue = "24h") String range) {
        return dashboard.summary(range);
    }

    @GetMapping("/dashboard/metrics")
    @Operation(summary = "Séries temporais, distribuições e rankings do período")
    public DashboardService.DashboardMetrics metrics(@RequestParam(defaultValue = "24h") String range) {
        return dashboard.metrics(range);
    }

    @GetMapping("/health/services")
    @Operation(summary = "Saúde dos serviços do laboratório")
    public ServiceHealthService.HealthReport services() {
        return health.check();
    }
}
