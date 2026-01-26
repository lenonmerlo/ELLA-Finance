package com.ella.backend.dto.dashboard;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementDashboardResponseDTO {
    private BankStatementDashboardSummaryDTO summary;
    private List<BankStatementDashboardTransactionDTO> transactions;
}
