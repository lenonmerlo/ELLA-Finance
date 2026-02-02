package com.ella.backend.services.insights.providers;

import java.util.List;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.Person;

public interface InsightProvider {
    List<InsightDTO> generate(Person person, int year, int month);
}
