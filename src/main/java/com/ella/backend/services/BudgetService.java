package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.budget.BudgetRequest;
import com.ella.backend.dto.budget.BudgetResponse;
import com.ella.backend.entities.Budget;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.BudgetRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    private final BudgetRepository budgetRepository;
    private final PersonRepository personRepository;

    public BudgetResponse createBudget(String personId, BudgetRequest request) {
        Person person = findPersonOrThrow(personId);

        budgetRepository.findByOwner(person)
                .ifPresent(existing -> {
                    throw new ConflictException("Orçamento já existe para esta pessoa");
                });

        validateRequest(request);

        Budget budget = new Budget();
        budget.setOwner(person);
        applyRequest(budget, request);
        calculateTotals(budget);
        calculatePercentages(budget);

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved);
    }

    public BudgetResponse updateBudget(String budgetId, BudgetRequest request) {
        UUID uuid = parseUuid(budgetId, "budgetId");
        Budget budget = budgetRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        validateRequest(request);

        applyRequest(budget, request);
        calculateTotals(budget);
        calculatePercentages(budget);

        Budget saved = budgetRepository.save(budget);
        return toResponse(saved);
    }

    public BudgetResponse getBudget(String personId) {
        Person person = findPersonOrThrow(personId);

        Budget budget = budgetRepository.findByOwner(person)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        return toResponse(budget);
    }

    private Person findPersonOrThrow(String personId) {
        UUID uuid = parseUuid(personId, "personId");
        return personRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));
    }

    private UUID parseUuid(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(fieldName + " inválido");
        }
    }

    private void validateRequest(BudgetRequest request) {
        if (request.getIncome() == null) throw new BadRequestException("Renda é obrigatória");
        if (request.getIncome().compareTo(ZERO) <= 0) throw new BadRequestException("Renda deve ser maior que zero");

        requireNonNegative(request.getEssentialFixedCost(), "Custo Fixo Essencial");
        requireNonNegative(request.getNecessaryFixedCost(), "Custo Fixo Necessário");
        requireNonNegative(request.getVariableFixedCost(), "Custo Fixo Variável");
        requireNonNegative(request.getInvestment(), "Investimento");
        requireNonNegative(request.getPlannedPurchase(), "Compra Programada");
        requireNonNegative(request.getProtection(), "Proteção");
    }

    private void requireNonNegative(BigDecimal value, String fieldLabel) {
        if (value == null) throw new BadRequestException(fieldLabel + " é obrigatório");
        if (value.compareTo(ZERO) < 0) throw new BadRequestException(fieldLabel + " não pode ser negativo");
    }

    private void applyRequest(Budget budget, BudgetRequest request) {
        budget.setIncome(money(request.getIncome()));
        budget.setEssentialFixedCost(money(request.getEssentialFixedCost()));
        budget.setNecessaryFixedCost(money(request.getNecessaryFixedCost()));
        budget.setVariableFixedCost(money(request.getVariableFixedCost()));
        budget.setInvestment(money(request.getInvestment()));
        budget.setPlannedPurchase(money(request.getPlannedPurchase()));
        budget.setProtection(money(request.getProtection()));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void calculateTotals(Budget budget) {
        BigDecimal total = ZERO
                .add(budget.getEssentialFixedCost())
                .add(budget.getNecessaryFixedCost())
                .add(budget.getVariableFixedCost())
                .add(budget.getInvestment())
                .add(budget.getPlannedPurchase())
                .add(budget.getProtection())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal balance = budget.getIncome()
                .subtract(total)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        budget.setTotal(total);
        budget.setBalance(balance);
    }

    private void calculatePercentages(Budget budget) {
        BigDecimal income = budget.getIncome();
        if (income == null || income.compareTo(ZERO) <= 0) {
            throw new BadRequestException("Renda deve ser maior que zero");
        }

        BigDecimal necessities = budget.getEssentialFixedCost().add(budget.getNecessaryFixedCost());
        BigDecimal desires = budget.getVariableFixedCost();
        BigDecimal investments = budget.getInvestment().add(budget.getPlannedPurchase()).add(budget.getProtection());

        budget.setNecessitiesPercentage(percentageOf(necessities, income));
        budget.setDesiresPercentage(percentageOf(desires, income));
        budget.setInvestmentsPercentage(percentageOf(investments, income));
    }

    private BigDecimal percentageOf(BigDecimal part, BigDecimal total) {
        return part
                .divide(total, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private String generateRecommendation(Budget budget) {
        List<String> warnings = new ArrayList<>();

        if (budget.getNecessitiesPercentage().compareTo(new BigDecimal("50.00")) > 0) {
            warnings.add("⚠️ Necessidades acima de 50%");
        }
        if (budget.getDesiresPercentage().compareTo(new BigDecimal("30.00")) > 0) {
            warnings.add("⚠️ Desejos acima de 30%");
        }
        if (budget.getInvestmentsPercentage().compareTo(new BigDecimal("20.00")) < 0) {
            warnings.add("⚠️ Investimentos abaixo de 20%");
        }

        if (warnings.isEmpty()) {
            return "✅ Excelente! Você está dentro da regra 50/30/20";
        }

        return String.join(" | ", warnings);
    }

    private boolean isHealthyBudget(Budget budget) {
        return budget.getNecessitiesPercentage().compareTo(new BigDecimal("50.00")) <= 0
                && budget.getDesiresPercentage().compareTo(new BigDecimal("30.00")) <= 0
                && budget.getInvestmentsPercentage().compareTo(new BigDecimal("20.00")) >= 0;
    }

    private BudgetResponse toResponse(Budget budget) {
        BudgetResponse response = new BudgetResponse();

        response.setId(budget.getId());

        response.setIncome(budget.getIncome());
        response.setEssentialFixedCost(budget.getEssentialFixedCost());
        response.setNecessaryFixedCost(budget.getNecessaryFixedCost());
        response.setVariableFixedCost(budget.getVariableFixedCost());
        response.setInvestment(budget.getInvestment());
        response.setPlannedPurchase(budget.getPlannedPurchase());
        response.setProtection(budget.getProtection());

        response.setTotal(budget.getTotal());
        response.setBalance(budget.getBalance());

        response.setNecessitiesPercentage(budget.getNecessitiesPercentage());
        response.setDesiresPercentage(budget.getDesiresPercentage());
        response.setInvestmentsPercentage(budget.getInvestmentsPercentage());

        response.setRecommendation(generateRecommendation(budget));
        response.setHealthy(isHealthyBudget(budget));

        response.setCreatedAt(budget.getCreatedAt());
        response.setUpdatedAt(budget.getUpdatedAt());

        return response;
    }
}
