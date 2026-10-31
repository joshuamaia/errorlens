package dev.errorlens.domain.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Diagnóstico persistido produzido pelo Diagnosis Engine. Listas são armazenadas como JSON.
 */
@Entity
@Table(name = "diagnoses")
public class DiagnosisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(nullable = false)
    private int rank;

    @Column(name = "rule_id", nullable = false)
    private String ruleId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String confidence;

    @Column(name = "confidence_score", nullable = false)
    private int confidenceScore;

    @Column(name = "possible_causes", nullable = false)
    private String possibleCauses;

    @Column(nullable = false)
    private String evidences;

    @Column(nullable = false)
    private String suggestions;

    @Column(nullable = false)
    private String fixes;

    @Column(name = "code_example")
    private String codeExample;

    protected DiagnosisRecord() {
    }

    public DiagnosisRecord(int rank, String ruleId, String title, String category, String description,
                           String confidence, int confidenceScore, String possibleCauses, String evidences,
                           String suggestions, String fixes, String codeExample) {
        this.rank = rank;
        this.ruleId = ruleId;
        this.title = title;
        this.category = category;
        this.description = description;
        this.confidence = confidence;
        this.confidenceScore = confidenceScore;
        this.possibleCauses = possibleCauses;
        this.evidences = evidences;
        this.suggestions = suggestions;
        this.fixes = fixes;
        this.codeExample = codeExample;
    }

    void attachTo(Execution execution) {
        this.execution = execution;
    }

    public Long getId() {
        return id;
    }

    public int getRank() {
        return rank;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getConfidence() {
        return confidence;
    }

    public int getConfidenceScore() {
        return confidenceScore;
    }

    public String getPossibleCauses() {
        return possibleCauses;
    }

    public String getEvidences() {
        return evidences;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public String getFixes() {
        return fixes;
    }

    public String getCodeExample() {
        return codeExample;
    }
}
