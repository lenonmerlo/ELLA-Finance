package com.ella.backend.privacy.dto;

import java.time.Instant;

public record ConsentResponseDTO(String contractVersion, Instant acceptedAt) {
}
