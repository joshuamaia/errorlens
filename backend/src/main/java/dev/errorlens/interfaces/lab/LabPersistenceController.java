package dev.errorlens.interfaces.lab;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.lab.PersistenceArchiveService;
import dev.errorlens.interfaces.lab.dto.LabDtos.ArchiveRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lab/persistence")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Laboratório · Persistência", description = "Falha isolada de persistência (D03)")
public class LabPersistenceController {

    private final PersistenceArchiveService archiveService;

    public LabPersistenceController(PersistenceArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @PostMapping("/archive")
    @Operation(summary = "Arquiva um cliente (depende de tabela inexistente)")
    public ResponseEntity<Map<String, Object>> archive(@Valid @RequestBody ArchiveRequest request) {
        archiveService.archive(request.customerId(), request.reason());
        return ResponseEntity.accepted().body(Map.of("customerId", request.customerId(), "archived", true));
    }
}
