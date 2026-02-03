package com.ella.backend.dto.reports;

import com.ella.backend.enums.ReportType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateReportRequestDTO {

    @NotNull
    private ReportType type;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer month;
}
