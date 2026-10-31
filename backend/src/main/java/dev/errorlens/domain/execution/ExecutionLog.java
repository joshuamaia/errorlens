package dev.errorlens.domain.execution;

import java.time.Instant;

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
 * Linha de log (sanitizada) capturada com o MDC da execução.
 */
@Entity
@Table(name = "execution_logs")
public class ExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @Column(nullable = false)
    private int seq;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String logger;

    @Column
    private String thread;

    @Column(nullable = false)
    private String message;

    @Column(name = "exception_class")
    private String exceptionClass;

    protected ExecutionLog() {
    }

    public ExecutionLog(int seq, Instant loggedAt, String level, String logger, String thread, String message,
                        String exceptionClass) {
        this.seq = seq;
        this.loggedAt = loggedAt;
        this.level = level;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
        this.exceptionClass = exceptionClass;
    }

    void attachTo(Execution execution) {
        this.execution = execution;
    }

    public Long getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public String getLevel() {
        return level;
    }

    public String getLogger() {
        return logger;
    }

    public String getThread() {
        return thread;
    }

    public String getMessage() {
        return message;
    }

    public String getExceptionClass() {
        return exceptionClass;
    }
}
