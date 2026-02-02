package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.insights.providers.InsightProvider;

@ExtendWith(MockitoExtension.class)
class DashboardInsightsServiceTest {

    @Mock
    private PersonRepository personRepository;

    @Mock
    private InsightProvider provider1;

    @Mock
    private InsightProvider provider2;

    @Test
    @DisplayName("Does not fail if one provider throws")
    void providerFailureDoesNotBreakResponse() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        person.setName("Test");

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(provider1.generate(eq(person), eq(2026), eq(2))).thenThrow(new RuntimeException("boom"));
        when(provider2.generate(eq(person), eq(2026), eq(2))).thenReturn(List.of(
                InsightDTO.builder().type("info").category("Gastos").message("ok").build()
        ));

        DashboardInsightsService service = new DashboardInsightsService(List.of(provider1, provider2), personRepository);

        List<InsightDTO> out = service.getInsights(personId.toString(), 2026, 2);
        assertEquals(1, out.size());
        assertEquals("ok", out.getFirst().getMessage());
    }

    @Test
    @DisplayName("Limits to 5 insights")
    void limitsToFive() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        person.setName("Test");

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        List<InsightDTO> many = List.of(
                i("1"), i("2"), i("3"), i("4"), i("5"), i("6")
        );

        when(provider1.generate(eq(person), eq(2026), eq(2))).thenReturn(many);
        when(provider2.generate(eq(person), eq(2026), eq(2))).thenReturn(List.of());

        DashboardInsightsService service = new DashboardInsightsService(List.of(provider1, provider2), personRepository);

        List<InsightDTO> out = service.getInsights(personId.toString(), 2026, 2);
        assertEquals(5, out.size());
    }

    @Test
    @DisplayName("Throws ResourceNotFoundException when person does not exist")
    void throwsWhenPersonNotFound() {
        UUID personId = UUID.randomUUID();
        when(personRepository.findById(personId)).thenReturn(Optional.empty());

        DashboardInsightsService service = new DashboardInsightsService(List.of(provider1, provider2), personRepository);

        assertThrows(ResourceNotFoundException.class, () -> service.getInsights(personId.toString(), 2026, 2));
    }

    private static InsightDTO i(String msg) {
        return InsightDTO.builder().type("info").category("C").message(msg).build();
    }
}
