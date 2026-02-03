package com.ella.backend.dto.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ella.backend.enums.AssetType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssetRequest {

    @NotBlank
    private String name;

    @NotNull
    private AssetType type;

    private BigDecimal purchaseValue;

    @NotNull
    private BigDecimal currentValue;

    private LocalDate purchaseDate;
}
