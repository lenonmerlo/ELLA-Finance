package com.ella.backend.dto.reports;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryTotalDTO {
    private String category;
    private BigDecimal amount;
    private BigDecimal percent;
}
