package dev.errorlens.application.lab;

import dev.errorlens.domain.exception.InvoiceDataException;

/**
 * Interpreta metadados no formato {@code parcelas=N}. Converte a NumberFormatException em
 * InvoiceDataException, preservando a causa original.
 */
final class InvoiceMetadataParser {

    private InvoiceMetadataParser() {
    }

    static int installments(String metadata) {
        if (metadata == null || !metadata.startsWith("parcelas=")) {
            throw new InvoiceDataException("Metadado de faturamento ausente ou fora do formato 'parcelas=N'.", null);
        }
        String value = metadata.substring("parcelas=".length());
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new InvoiceDataException("Metadado de faturamento inválido: 'parcelas' deve ser numérico.", ex);
        }
    }
}
