package dev.errorlens.application.lab;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.errorlens.domain.lab.Customer;

/**
 * Resumo do programa de fidelidade. O catálogo de perfis retorna {@code null} para clientes
 * sem tier (contrato legado), e o cálculo não trata esse caso: origem real da
 * NullPointerException do cenário E01.
 */
@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    private static final Map<String, LoyaltyProfile> PROFILES = Map.of(
            "BRONZE", new LoyaltyProfile("BRONZE", 1, List.of("Frete reduzido")),
            "SILVER", new LoyaltyProfile("SILVER", 2, List.of("Frete grátis", "Atendimento prioritário")),
            "GOLD", new LoyaltyProfile("GOLD", 3, List.of("Frete grátis", "Cashback de 5%", "Gerente dedicado")));

    private final CustomerService customerService;

    public LoyaltyService(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public LoyaltySummary summarize(Long customerId) {
        Customer customer = customerService.findById(customerId);
        log.info("Calculando resumo de fidelidade do cliente id={}", customerId);
        LoyaltyProfile profile = findProfile(customer.getLoyaltyTier());
        BigDecimal points = customer.getCreditLimit()
                .multiply(BigDecimal.valueOf(profile.pointsMultiplier()))
                .divide(BigDecimal.TEN, 0, RoundingMode.DOWN);
        return new LoyaltySummary(customer.getId(), customer.getName(), profile.tier(), points.longValue(),
                profile.benefits());
    }

    /**
     * Contrato legado: retorna null quando o tier não existe.
     */
    LoyaltyProfile findProfile(String tier) {
        return tier == null ? null : PROFILES.get(tier);
    }

    public record LoyaltyProfile(String tier, int pointsMultiplier, List<String> benefits) {
    }

    public record LoyaltySummary(Long customerId, String customerName, String tier, long points,
                                 List<String> benefits) {
    }
}
