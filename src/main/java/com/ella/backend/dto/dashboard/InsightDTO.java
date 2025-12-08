package com.ella.backend.dto.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InsightDTO {
    private String type; // warning, success, info
    private String message;
    private String category;
}
