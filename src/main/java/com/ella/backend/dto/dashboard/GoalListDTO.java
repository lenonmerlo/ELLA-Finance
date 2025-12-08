package com.ella.backend.dto.dashboard;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GoalListDTO {
    private List<GoalProgressDTO> goals;
}
