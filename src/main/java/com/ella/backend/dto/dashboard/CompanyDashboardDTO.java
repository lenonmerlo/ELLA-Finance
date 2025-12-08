package com.ella.backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDashboardDTO {

    private String companyId;
    private String companyName;

    private SummaryDTO summary;
    private TotalsDTO totals;
    private List<CategoryBreakdownDTO> categoryBreakdown;
    private MonthlyEvolutionDTO monthlyEvolution;
    private List<InvoiceSummaryDTO> invoices;
}