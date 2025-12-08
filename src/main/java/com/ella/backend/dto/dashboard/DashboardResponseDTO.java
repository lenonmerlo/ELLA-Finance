package com.ella.backend.dto.dashboard;

import com.ella.backend.dto.FinancialTransactionResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDTO {

    private String personId;

    // Bloco pessoal (PF)
    private SummaryDTO personalSummary;
    private TotalsDTO personalTotals;
    private List<CategoryBreakdownDTO> personalCategoryBreakdown;
    private MonthlyEvolutionDTO personalMonthlyEvolution;
    private GoalProgressDTO goalProgress;
    private List<InvoiceSummaryDTO> personalInvoices;
    
    // ✅ NOVO: Lista de transações individuais do período
    private List<FinancialTransactionResponseDTO> personalTransactions;

    // Bloco empresarial (lista de empresas)
    private List<CompanyDashboardDTO> companies;

    // Totais consolidados PF + PJ
    private ConsolidatedTotalsDTO consolidatedTotals;
}
