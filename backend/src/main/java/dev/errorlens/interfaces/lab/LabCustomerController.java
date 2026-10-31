package dev.errorlens.interfaces.lab;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.lab.CustomerService;
import dev.errorlens.application.lab.LoyaltyService;
import dev.errorlens.domain.lab.Customer;
import dev.errorlens.interfaces.lab.dto.LabDtos.CreateCustomerRequest;
import dev.errorlens.interfaces.lab.dto.LabDtos.CustomerResponse;
import dev.errorlens.interfaces.lab.dto.LabDtos.PageResponse;
import dev.errorlens.interfaces.lab.dto.LabDtos.UpdateCustomerRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * API real de clientes do laboratório, exercitada pelos cenários A, B, D e E.
 * Aceita apenas GET/POST na coleção (PATCH gera 405 - cenário B05).
 */
@RestController
@RequestMapping("/api/v1/lab/customers")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Laboratório · Clientes", description = "Endpoints reais usados pelos cenários (somente perfil lab)")
public class LabCustomerController {

    private final CustomerService customerService;
    private final LoyaltyService loyaltyService;

    public LabCustomerController(CustomerService customerService, LoyaltyService loyaltyService) {
        this.customerService = customerService;
        this.loyaltyService = loyaltyService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um cliente")
    public CustomerResponse findById(@PathVariable Long id) {
        return CustomerResponse.from(customerService.findById(id));
    }

    @GetMapping
    @Operation(summary = "Lista clientes com paginação")
    public PageResponse<CustomerResponse> list(@PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return PageResponse.from(customerService.list(pageable).map(CustomerResponse::from));
    }

    @PostMapping
    @Operation(summary = "Cria um cliente")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer created = customerService.create(request.name(), request.email(), request.document(),
                request.creditLimit());
        return ResponseEntity.created(URI.create("/api/v1/lab/customers/" + created.getId()))
                .body(CustomerResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um cliente")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return CustomerResponse.from(customerService.update(id, request.name(), request.status(),
                request.creditLimit()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Exclui um cliente")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/loyalty")
    @Operation(summary = "Resumo do programa de fidelidade")
    public LoyaltyService.LoyaltySummary loyalty(@PathVariable Long id) {
        return loyaltyService.summarize(id);
    }
}
