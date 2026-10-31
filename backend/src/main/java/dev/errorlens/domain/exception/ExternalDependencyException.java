package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

public class ExternalDependencyException extends ErrorLensException {

    public ExternalDependencyException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "DEPENDENCY_FAILURE", message, cause);
    }
}
