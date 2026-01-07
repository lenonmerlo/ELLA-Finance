package com.ella.backend.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightDTO {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String severity;
    private boolean actionable;
    private LocalDate generatedAt;
    private LocalDate startDate;
    private LocalDate endDate;
}
