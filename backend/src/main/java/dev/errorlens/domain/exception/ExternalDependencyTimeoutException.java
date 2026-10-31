package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

public class ExternalDependencyTimeoutException extends ErrorLensException {

    public ExternalDependencyTimeoutException(String message, Throwable cause) {
        super(HttpStatus.GATEWAY_TIMEOUT, "DEPENDENCY_TIMEOUT", message, cause);
    }
}
