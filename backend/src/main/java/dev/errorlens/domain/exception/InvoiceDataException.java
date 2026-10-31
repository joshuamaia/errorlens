package dev.errorlens.domain.exception;

/**
 * Erro intermediário de interpretação dos metadados de faturamento (nível do meio da cadeia).
 */
public class InvoiceDataException extends RuntimeException {

    public InvoiceDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
