package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Entrada rejeitada pelas validações das ferramentas de análise (tamanho, formato, tipo de arquivo).
 */
public class InvalidInputException extends ErrorLensException {

    public InvalidInputException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public InvalidInputException(HttpStatus status, String errorCode, String message) {
        super(status, errorCode, message);
    }
}
