package dev.errorlens.domain.exception;

import org.springframework.http.HttpStatus;

/**
 * Falha na reserva de estoque durante o checkout. Lançada dentro da transação,
 * provoca rollback de todas as alterações já enviadas ao banco.
 */
public class InventoryReservationException extends ErrorLensException {

    public InventoryReservationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "INVENTORY_RESERVATION_FAILED", message);
    }
}
