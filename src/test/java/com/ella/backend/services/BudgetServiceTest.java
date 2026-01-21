package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ella.backend.dto.budget.BudgetRequest;
import com.ella.backend.dto.budget.BudgetResponse;
import com.ella.backend.entities.Budget;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.repositories.BudgetRepository;
import com.ella.backend.repositories.PersonRepository;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void createBudget_validValues_calculatesTotalsAndPercentages() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        person.setName("Teste");

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(budgetRepository.findByOwner(person)).thenReturn(Optional.empty());

        BudgetRequest req = request(
                "10000",
                "3000",
                "1000",
                "2000",
                "1500",
                "500",
                "500"
        );

        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            if (b.getId() == null) b.setId(UUID.randomUUID());
            b.setCreatedAt(LocalDateTime.now());
            b.setUpdatedAt(LocalDateTime.now());
            return b;
        });

        BudgetResponse resp = budgetService.createBudget(personId.toString(), req);

        assertNotNull(resp.getId());
        assertEquals(new BigDecimal("8500.00"), resp.getTotal());
        assertEquals(new BigDecimal("1500.00"), resp.getBalance());
        assertEquals(new BigDecimal("40.00"), resp.getNecessitiesPercentage());
        assertEquals(new BigDecimal("20.00"), resp.getDesiresPercentage());
        assertEquals(new BigDecimal("25.00"), resp.getInvestmentsPercentage());
        assertTrue(resp.isHealthy());
        assertEquals("✅ Excelente! Você está dentro da regra 50/30/20", resp.getRecommendation());

        verify(budgetRepository).save(any(Budget.class));
    }

    @Test
    void createBudget_invalidIncome_throwsBadRequest() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(budgetRepository.findByOwner(person)).thenReturn(Optional.empty());

        BudgetRequest req = request(
                "0",
                "100",
                "0",
                "0",
                "0",
                "0",
                "0"
        );

        assertThrows(BadRequestException.class, () -> budgetService.createBudget(personId.toString(), req));
    }

    @Test
    void updateBudget_recalculatesValues() {
        UUID budgetId = UUID.randomUUID();

        Person owner = new Person();
        owner.setId(UUID.randomUUID());
        owner.setName("Teste");

        Budget existing = Budget.builder()
                .id(budgetId)
            .owner(owner)
                .income(new BigDecimal("10000.00"))
                .essentialFixedCost(new BigDecimal("3000.00"))
                .necessaryFixedCost(new BigDecimal("1000.00"))
                .variableFixedCost(new BigDecimal("2000.00"))
                .investment(new BigDecimal("1500.00"))
                .plannedPurchase(new BigDecimal("500.00"))
                .protection(new BigDecimal("500.00"))
                .total(new BigDecimal("8500.00"))
                .balance(new BigDecimal("1500.00"))
                .necessitiesPercentage(new BigDecimal("40.00"))
                .desiresPercentage(new BigDecimal("20.00"))
                .investmentsPercentage(new BigDecimal("25.00"))
                .build();

        when(budgetRepository.findById(budgetId)).thenReturn(Optional.of(existing));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetRequest req = request(
                "10000",
                "3000",
                "1000",
                "3000",
                "1500",
                "500",
                "500"
        );

        BudgetResponse resp = budgetService.updateBudget(budgetId.toString(), req);

        assertEquals(new BigDecimal("9500.00"), resp.getTotal());
        assertEquals(new BigDecimal("500.00"), resp.getBalance());
        assertEquals(new BigDecimal("40.00"), resp.getNecessitiesPercentage());
        assertEquals(new BigDecimal("30.00"), resp.getDesiresPercentage());
        assertEquals(new BigDecimal("25.00"), resp.getInvestmentsPercentage());
        assertTrue(resp.isHealthy());
    }

    @Test
    void getBudget_returnsMappedResponse_withWarnings() {
        UUID personId = UUID.randomUUID();
        Person person = new Person();
        person.setId(personId);

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .owner(person)
                .income(new BigDecimal("10000.00"))
                .essentialFixedCost(new BigDecimal("6000.00"))
                .necessaryFixedCost(new BigDecimal("0.00"))
                .variableFixedCost(new BigDecimal("2000.00"))
                .investment(new BigDecimal("500.00"))
                .plannedPurchase(new BigDecimal("0.00"))
                .protection(new BigDecimal("0.00"))
                .total(new BigDecimal("8500.00"))
                .balance(new BigDecimal("1500.00"))
                .necessitiesPercentage(new BigDecimal("60.00"))
                .desiresPercentage(new BigDecimal("20.00"))
                .investmentsPercentage(new BigDecimal("5.00"))
                .build();

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(budgetRepository.findByOwner(person)).thenReturn(Optional.of(budget));

        BudgetResponse resp = budgetService.getBudget(personId.toString());

        assertEquals(budget.getId(), resp.getId());
        assertTrue(resp.getRecommendation().contains("Necessidades acima de 50%"));
        assertTrue(resp.getRecommendation().contains("Investimentos abaixo de 20%"));
        assertTrue(!resp.isHealthy());
    }

    private BudgetRequest request(
            String income,
            String essential,
            String necessary,
            String variable,
            String investment,
            String plannedPurchase,
            String protection
    ) {
        BudgetRequest req = new BudgetRequest();
        req.setIncome(new BigDecimal(income));
        req.setEssentialFixedCost(new BigDecimal(essential));
        req.setNecessaryFixedCost(new BigDecimal(necessary));
        req.setVariableFixedCost(new BigDecimal(variable));
        req.setInvestment(new BigDecimal(investment));
        req.setPlannedPurchase(new BigDecimal(plannedPurchase));
        req.setProtection(new BigDecimal(protection));
        return req;
    }
}
