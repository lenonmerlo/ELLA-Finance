package com.ella.backend.services.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.GoalStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.PersonRepository;
import com.ella.backend.services.GoalGeneratorService;
import com.ella.backend.services.cashflow.CashflowTransactionsService;
import com.ella.backend.services.goals.providers.GoalProvider;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"null", "unchecked"})
class GoalGeneratorServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private CashflowTransactionsService cashflowTransactionsService;

    @Mock
    private GoalProvider provider1;

    @Mock
    private GoalProvider provider2;

    @Mock
    private GoalProvider provider3;

    @Test
    @DisplayName("Generates up to (4 - activeGoals) goals")
    void generatesUpToRemainingSlots() {
        Person person = person(UUID.randomUUID());

        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(goalRepository.countByOwnerAndStatus(person, GoalStatus.ACTIVE)).thenReturn(2L);
        when(goalRepository.findByOwner(person)).thenReturn(List.of());

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(
            org.mockito.ArgumentMatchers.eq(person),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        when(provider1.getPriority()).thenReturn(1);
        when(provider2.getPriority()).thenReturn(2);
        when(provider3.getPriority()).thenReturn(3);

        when(provider1.generateGoals(person, List.of())).thenReturn(List.of(goal(person, "G1")));
        when(provider2.generateGoals(person, List.of())).thenReturn(List.of(goal(person, "G2")));

        when(goalRepository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
            StreamSupport.stream(((Iterable<Goal>) inv.getArgument(0)).spliterator(), false).toList()
        );

        GoalGeneratorService service = new GoalGeneratorService(
            List.of(provider1, provider2, provider3),
            personRepository,
            goalRepository,
            financialTransactionRepository,
            cashflowTransactionsService
        );

        List<Goal> saved = service.generateGoalsForUser(person.getId().toString());

        // active=2 => salva no máximo 2
        assertEquals(2, saved.size());
        verify(goalRepository).saveAll(org.mockito.ArgumentMatchers.any());
        verify(provider3, never()).generateGoals(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Does not generate when already has 4 active goals")
    void doesNotGenerateWhenAlreadyFull() {
        Person person = person(UUID.randomUUID());

        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(goalRepository.countByOwnerAndStatus(person, GoalStatus.ACTIVE)).thenReturn(4L);

        GoalGeneratorService service = new GoalGeneratorService(
            List.of(provider1, provider2, provider3),
            personRepository,
            goalRepository,
            financialTransactionRepository,
            cashflowTransactionsService
        );

        List<Goal> saved = service.generateGoalsForUser(person.getId().toString());

        assertEquals(0, saved.size());
        verify(goalRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Deduplicates by title against existing active goals")
    void deduplicatesAgainstExistingActiveGoals() {
        Person person = person(UUID.randomUUID());

        Goal existingActive = goal(person, "Orçamento: iFood");
        existingActive.setStatus(GoalStatus.ACTIVE);

        when(personRepository.findById(person.getId())).thenReturn(Optional.of(person));
        when(goalRepository.countByOwnerAndStatus(person, GoalStatus.ACTIVE)).thenReturn(0L);
        when(goalRepository.findByOwner(person)).thenReturn(List.of(existingActive));

        when(financialTransactionRepository.findByPersonAndTransactionDateBetween(
            org.mockito.ArgumentMatchers.eq(person),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        when(provider1.getPriority()).thenReturn(1);
        when(provider2.getPriority()).thenReturn(2);
        when(provider1.generateGoals(person, List.of())).thenReturn(List.of(goal(person, "Orçamento: iFood")));
        when(provider2.generateGoals(person, List.of())).thenReturn(List.of(goal(person, "Orçamento: Mercado")));

        when(goalRepository.saveAll(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
            StreamSupport.stream(((Iterable<Goal>) inv.getArgument(0)).spliterator(), false).toList()
        );

        GoalGeneratorService service = new GoalGeneratorService(
            List.of(provider1, provider2),
            personRepository,
            goalRepository,
            financialTransactionRepository,
            cashflowTransactionsService
        );

        List<Goal> saved = service.generateGoalsForUser(person.getId().toString());

        assertEquals(1, saved.size());
        assertEquals("Orçamento: Mercado", saved.getFirst().getTitle());

        ArgumentCaptor<Iterable<Goal>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(goalRepository).saveAll(captor.capture());
        List<Goal> captured = StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        assertEquals(1, captured.size());
    }

    @Test
    @DisplayName("Throws when person is not found")
    void throwsWhenPersonNotFound() {
        UUID id = UUID.randomUUID();
        when(personRepository.findById(id)).thenReturn(Optional.empty());

        GoalGeneratorService service = new GoalGeneratorService(
            List.of(provider1),
            personRepository,
            goalRepository,
            financialTransactionRepository,
            cashflowTransactionsService
        );

        assertThrows(ResourceNotFoundException.class, () -> service.generateGoalsForUser(id.toString()));
    }

    private static Person person(UUID id) {
        Person p = new Person();
        p.setId(id);
        p.setName("Test");
        return p;
    }

    private static Goal goal(Person owner, String title) {
        Goal g = new Goal();
        g.setOwner(owner);
        g.setTitle(title);
        g.setTargetAmount(new BigDecimal("100"));
        g.setCurrentAmount(BigDecimal.ZERO);
        g.setDeadline(LocalDate.now().plusDays(30));
        g.setStatus(GoalStatus.ACTIVE);
        return g;
    }
}
