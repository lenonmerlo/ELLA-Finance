package com.ella.backend.dto.investment;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class InvestmentSummaryResponse {
    private BigDecimal totalInvested;
    private BigDecimal totalCurrent;
    private BigDecimal totalProfitability;
    private List<InvestmentResponse> investments;
}
