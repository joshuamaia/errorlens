package dev.errorlens.interfaces.lab;

import java.math.BigDecimal;
import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.errorlens.application.lab.OrderService;
import dev.errorlens.interfaces.lab.dto.LabDtos.CheckoutRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lab/orders")
@ConditionalOnBooleanProperty(prefix = "errorlens.lab", name = "enabled", matchIfMissing = true)
@Tag(name = "Laboratório · Pedidos", description = "Checkout transacional, desconto e faturamento")
public class LabOrderController {

    private final OrderService orderService;

    public LabOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @Operation(summary = "Registra pedido e debita crédito em uma única transação")
    public ResponseEntity<OrderService.CheckoutResult> checkout(@Valid @RequestBody CheckoutRequest request) {
        OrderService.CheckoutResult result = orderService.checkout(request.customerId(), request.sku(),
                request.quantity(), request.amount(), request.description());
        return ResponseEntity.created(URI.create("/api/v1/lab/orders/" + result.orderId())).body(result);
    }

    @GetMapping("/discount-simulation")
    @Operation(summary = "Simula desconto percentual")
    public OrderService.DiscountSimulation discount(@RequestParam(defaultValue = "1000.00") BigDecimal amount,
                                                    @RequestParam int percentage) {
        return orderService.simulateDiscount(amount, percentage);
    }

    @PostMapping("/{id}/invoice")
    @Operation(summary = "Gera a fatura do pedido")
    public OrderService.InvoiceResult invoice(@PathVariable Long id) {
        return orderService.generateInvoice(id);
    }
}
