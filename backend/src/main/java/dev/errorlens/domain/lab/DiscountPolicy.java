package dev.errorlens.domain.lab;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Objeto de valor que valida o percentual no construtor (cenário E02).
 */
public record DiscountPolicy(int percentage) {

    public DiscountPolicy {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException(
                    "Percentual de desconto deve estar entre 0 e 100, recebido: " + percentage);
        }
    }

    public BigDecimal apply(BigDecimal amount) {
        BigDecimal discount = amount.multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return amount.subtract(discount);
    }
}
