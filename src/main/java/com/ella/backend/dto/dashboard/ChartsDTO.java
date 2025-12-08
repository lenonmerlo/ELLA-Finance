package com.ella.backend.dto.dashboard;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartsDTO {
    private MonthlyEvolutionDTO monthlyEvolution;
    private List<CategoryBreakdownDTO> categoryBreakdown;
}
