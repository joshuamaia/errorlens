package dev.errorlens.application.lab;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.domain.exception.InvoiceDataException;
import dev.errorlens.domain.exception.InvoiceGenerationException;
import dev.errorlens.domain.exception.ResourceNotFoundException;
import dev.errorlens.domain.lab.Customer;
import dev.errorlens.domain.lab.DiscountPolicy;
import dev.errorlens.domain.lab.Order;
import dev.errorlens.infrastructure.persistence.lab.CustomerRepository;
import dev.errorlens.infrastructure.persistence.lab.OrderRepository;

/**
 * Operações de pedidos: checkout transacional (D04), simulação de desconto (E02) e
 * geração de fatura com exception encadeada (E03).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final InventoryService inventory;

    public OrderService(CustomerRepository customers, OrderRepository orders, InventoryService inventory) {
        this.customers = customers;
        this.orders = orders;
        this.inventory = inventory;
    }

    /**
     * Insere o pedido e debita o limite de crédito do cliente, forçando o flush para que os
     * comandos SQL cheguem ao banco; só então reserva o estoque. Uma falha na reserva reverte
     * toda a transação.
     */
    @Transactional
    public CheckoutResult checkout(Long customerId, String sku, int quantity, BigDecimal amount,
                                   String description) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.customer(customerId));
        log.info("Checkout iniciado: cliente id={}, SKU {}, quantidade {}", customerId, sku, quantity);
        Order order = orders.saveAndFlush(new Order(customer, description, amount, "parcelas=1"));
        log.info("Pedido id={} inserido (ainda não confirmado)", order.getId());
        customer.debitCredit(amount);
        customers.saveAndFlush(customer);
        log.info("Limite de crédito do cliente id={} atualizado para {}", customerId, customer.getCreditLimit());
        inventory.reserve(sku, quantity);
        log.info("Checkout concluído para o pedido id={}", order.getId());
        return new CheckoutResult(order.getId(), customerId, sku, quantity, amount, customer.getCreditLimit());
    }

    public DiscountSimulation simulateDiscount(BigDecimal amount, int percentage) {
        log.info("Simulando desconto de {}% sobre {}", percentage, amount);
        DiscountPolicy policy = new DiscountPolicy(percentage);
        return new DiscountSimulation(amount, percentage, policy.apply(amount));
    }

    @Transactional(readOnly = true)
    public InvoiceResult generateInvoice(Long orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> ResourceNotFoundException.order(orderId));
        log.info("Gerando fatura do pedido id={}", orderId);
        try {
            int installments = InvoiceMetadataParser.installments(order.getInvoiceMetadata());
            BigDecimal installmentValue = order.getAmount()
                    .divide(BigDecimal.valueOf(installments), 2, java.math.RoundingMode.HALF_UP);
            return new InvoiceResult(order.getId(), order.getCustomer().getName(), order.getAmount(), installments,
                    installmentValue);
        } catch (InvoiceDataException ex) {
            log.error("Falha ao interpretar metadados de faturamento do pedido id={}", orderId);
            throw new InvoiceGenerationException("Não foi possível gerar a fatura do pedido " + orderId + ".", ex);
        }
    }

    public record CheckoutResult(Long orderId, Long customerId, String sku, int quantity, BigDecimal amount,
                                 BigDecimal remainingCredit) {
    }

    public record DiscountSimulation(BigDecimal amount, int percentage, BigDecimal finalAmount) {
    }

    public record InvoiceResult(Long orderId, String customerName, BigDecimal amount, int installments,
                                BigDecimal installmentValue) {
    }
}
