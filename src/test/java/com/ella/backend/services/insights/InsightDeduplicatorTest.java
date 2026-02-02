package com.ella.backend.services.insights;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ella.backend.dto.dashboard.InsightDTO;

class InsightDeduplicatorTest {

    @Test
    @DisplayName("Deduplicates by category+message and keeps first occurrence")
    void deduplicates() {
        InsightDTO a1 = InsightDTO.builder().type("info").category("Gastos").message("A").build();
        InsightDTO a2 = InsightDTO.builder().type("warning").category("Gastos").message("A").build();
        InsightDTO b = InsightDTO.builder().type("info").category("Tendência").message("B").build();

        List<InsightDTO> out = InsightDeduplicator.deduplicate(List.of(a1, a2, b));

        assertEquals(2, out.size());
        assertEquals("A", out.getFirst().getMessage());
        assertEquals("B", out.get(1).getMessage());
    }
}
