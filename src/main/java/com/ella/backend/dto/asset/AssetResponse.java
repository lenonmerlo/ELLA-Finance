package com.ella.backend.dto.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import com.ella.backend.enums.AssetType;

import lombok.Data;

@Data
public class AssetResponse {
    private UUID id;
    private String name;
    private AssetType type;
    private BigDecimal purchaseValue;
    private BigDecimal currentValue;
    private LocalDate purchaseDate;

    private boolean syncedFromInvestment;
    private UUID investmentId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
