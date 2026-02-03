package com.ella.backend.dto.reports;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.ella.backend.enums.ReportType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReportListItemDTO {
    private UUID id;
    private ReportType type;
    private String title;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private OffsetDateTime createdAt;
}
