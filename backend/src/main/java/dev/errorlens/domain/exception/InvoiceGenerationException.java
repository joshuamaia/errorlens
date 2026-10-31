package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

public class InvoiceGenerationException extends ErrorLensException {

    public InvoiceGenerationException(String message, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "INVOICE_GENERATION_FAILED", message, cause);
    }
}
