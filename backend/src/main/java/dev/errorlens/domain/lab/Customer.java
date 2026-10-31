package dev.errorlens.domain.lab;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Cliente fictício do laboratório.
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 20)
    private String document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "loyalty_tier", length = 20)
    private String loyaltyTier;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerOrigin origin = CustomerOrigin.SCENARIO;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {
    }

    public Customer(String name, String email, String document, BigDecimal creditLimit, CustomerOrigin origin) {
        this.name = name;
        this.email = email;
        this.document = document;
        this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        this.origin = origin;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String name, CustomerStatus status, BigDecimal creditLimit) {
        this.name = name;
        this.status = status;
        this.creditLimit = creditLimit;
    }

    public void debitCredit(BigDecimal amount) {
        this.creditLimit = this.creditLimit.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getDocument() {
        return document;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public String getLoyaltyTier() {
        return loyaltyTier;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public CustomerOrigin getOrigin() {
        return origin;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
