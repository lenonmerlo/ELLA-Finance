package com.ella.backend.dto.reports;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ella.backend.enums.ReportType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportResponseDTO {
    private UUID id;
    private UUID personId;
    private ReportType type;
    private String title;

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate referenceDate;

    private OffsetDateTime createdAt;

    private ReportSummaryDTO summary;
    private List<CategoryTotalDTO> expensesByCategory;
    private List<CategoryTotalDTO> incomesByCategory;

    private Map<String, Object> investments;
    private Map<String, Object> assets;
    private Map<String, Object> goals;

    private Map<String, Object> budget;
    private Map<String, Object> bankStatements;

    private List<Map<String, Object>> insights;
}
