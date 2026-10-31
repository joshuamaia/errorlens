package dev.errorlens.domain.scenario;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Definição de um cenário do catálogo (dados descritivos). O comportamento executável
 * fica no {@code ScenarioHandler} correspondente ao código.
 */
@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "expected_status", nullable = false)
    private int expectedStatus;

    @Column(name = "expected_outcome", nullable = false)
    private String expectedOutcome;

    @Column(name = "failure_type")
    private String failureType;

    @Column(nullable = false)
    private String difficulty;

    @Column(nullable = false)
    private String explanation;

    @Column(nullable = false)
    private String tags;

    @Column(name = "compare_supported", nullable = false)
    private boolean compareSupported;

    @Column(name = "success_variant")
    private String successVariant;

    @Column(name = "failure_variant")
    private String failureVariant;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean enabled;

    protected Scenario() {
    }

    public List<String> tagList() {
        return tags == null || tags.isBlank() ? List.of() : Arrays.stream(tags.split(",")).map(String::trim).toList();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public int getExpectedStatus() {
        return expectedStatus;
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getTags() {
        return tags;
    }

    public boolean isCompareSupported() {
        return compareSupported;
    }

    public String getSuccessVariant() {
        return successVariant;
    }

    public String getFailureVariant() {
        return failureVariant;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
