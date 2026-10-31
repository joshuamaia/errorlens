package dev.errorlens.application.lab;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.errorlens.domain.exception.InventoryReservationException;

/**
 * Reserva de estoque com catálogo fixo e determinístico. O SKU descontinuado não possui
 * saldo e provoca a falha que dispara o rollback do cenário D04.
 */
@Service
public class InventoryService {

    public static final String SKU_AVAILABLE = "SKU-NOTEBOOK-14";
    public static final String SKU_DISCONTINUED = "SKU-DISCONTINUED-01";

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final Map<String, Integer> STOCK = Map.of(
            SKU_AVAILABLE, 500,
            "SKU-MONITOR-34", 120,
            SKU_DISCONTINUED, 0);

    public void reserve(String sku, int quantity) {
        int available = STOCK.getOrDefault(sku, 0);
        log.info("Reservando {} unidade(s) do SKU {} (saldo disponível: {})", quantity, sku, available);
        if (available < quantity) {
            log.error("Reserva de estoque recusada para o SKU {}: saldo {} < quantidade {}", sku, available,
                    quantity);
            throw new InventoryReservationException(
                    "Não foi possível reservar " + quantity + " unidade(s) do SKU " + sku + ".");
        }
    }
}
