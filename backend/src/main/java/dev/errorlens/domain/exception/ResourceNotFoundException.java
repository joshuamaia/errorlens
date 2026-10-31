package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ErrorLensException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }

    public static ResourceNotFoundException customer(Object id) {
        return new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente " + id + " não encontrado.");
    }

    public static ResourceNotFoundException order(Object id) {
        return new ResourceNotFoundException("ORDER_NOT_FOUND", "Pedido " + id + " não encontrado.");
    }
}
