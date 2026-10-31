package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception base da aplicação: carrega o status HTTP e um código de erro estável
 * usado no Problem Details e no Diagnosis Engine.
 */
public abstract class ErrorLensException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ErrorLensException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ErrorLensException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
