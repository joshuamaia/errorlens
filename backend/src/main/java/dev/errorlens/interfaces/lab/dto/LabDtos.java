package dev.errorlens.interfaces.lab.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;

import dev.errorlens.domain.lab.Customer;
import dev.errorlens.domain.lab.CustomerStatus;

/**
 * DTOs dos endpoints de laboratório.
 */
public final class LabDtos {

    private LabDtos() {
    }

    public record CreateCustomerRequest(
            @NotBlank(message = "O nome é obrigatório") @Size(max = 120) String name,
            @NotBlank(message = "O e-mail é obrigatório") @Email(message = "O e-mail deve ser válido")
            @Size(max = 160) String email,
            @Pattern(regexp = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$", message = "Documento deve seguir o formato 000.000.000-00")
            String document,
            @NotNull(message = "O limite de crédito é obrigatório")
            @PositiveOrZero(message = "O limite de crédito não pode ser negativo")
            @Digits(integer = 10, fraction = 2) BigDecimal creditLimit) {
    }

    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull CustomerStatus status,
            @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal creditLimit) {
    }

    public record CustomerResponse(Long id, String name, String email, String document, CustomerStatus status,
                                   String loyaltyTier, BigDecimal creditLimit, String origin, long version,
                                   Instant createdAt, Instant updatedAt) {

        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
                    maskDocument(customer.getDocument()), customer.getStatus(), customer.getLoyaltyTier(),
                    customer.getCreditLimit(), customer.getOrigin().name(), customer.getVersion(),
                    customer.getCreatedAt(), customer.getUpdatedAt());
        }

        private static String maskDocument(String document) {
            if (document == null || document.length() < 3) {
                return document;
            }
            return "***.***.***-" + document.substring(document.length() - 2);
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                                  String sort) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                    page.getTotalPages(), page.getSort().toString());
        }
    }

    public record CheckoutRequest(
            @NotNull Long customerId,
            @NotBlank @Size(max = 40) String sku,
            @Min(1) @Max(100) int quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 200) String description) {
    }

    public record ArchiveRequest(@NotNull @Positive Long customerId, @NotBlank @Size(max = 200) String reason) {
    }
}
