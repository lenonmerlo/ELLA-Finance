package com.ella.backend.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.dashboard.InsightDTO;

@Service
public class DashboardInsightsService {

    public List<InsightDTO> getInsights(String personId, int year, int month) {
        // TODO: Implement real AI/Rule-based insights generation
        // For now, return some mock insights or empty list
        
        List<InsightDTO> insights = new ArrayList<>();
        
        // Example mock logic
        insights.add(InsightDTO.builder()
                .type("info")
                .message("Bem-vindo ao seu novo dashboard modular!")
                .category("General")
                .build());

        return insights;
    }
}
