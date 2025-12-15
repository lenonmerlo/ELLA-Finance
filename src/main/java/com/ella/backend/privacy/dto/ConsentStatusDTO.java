package com.ella.backend.privacy.dto;

public record ConsentStatusDTO(
        boolean hasConsent,
        String currentContractVersion
) {
}
