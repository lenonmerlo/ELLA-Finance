package com.ella.backend.privacy.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsentRequestDTO (@NotBlank String contractVersion) {
}
