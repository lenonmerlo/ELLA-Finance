package com.ella.backend.dto;

import java.util.UUID;

public record ApplyTripResponseDTO(
        UUID tripId,
        int updatedCount
) {
}
