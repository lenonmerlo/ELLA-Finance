package com.ella.backend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sugestão de agrupamento de transações como uma viagem.
 *
 * A aplicação é não-destrutiva: ao aplicar, a categoria original pode ser preservada como subcategoria.
 */
public record TripSuggestionDTO(
        UUID tripId,
        LocalDate startDate,
        LocalDate endDate,
        List<String> transactionIds,
        String message
) {
}
