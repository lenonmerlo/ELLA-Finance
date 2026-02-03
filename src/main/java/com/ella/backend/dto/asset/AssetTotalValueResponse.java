package com.ella.backend.dto.asset;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssetTotalValueResponse {
    private BigDecimal totalValue;
}
