package com.ella.backend.dto;

import java.util.List;
import java.util.UUID;

public record ApplyTripRequestDTO(
        UUID tripId,
        List<String> transactionIds
) {
}
