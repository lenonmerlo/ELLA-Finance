package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ella.backend.dto.investment.InvestmentRequest;
import com.ella.backend.dto.investment.InvestmentResponse;
import com.ella.backend.dto.investment.InvestmentSummaryResponse;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvestmentService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final int DIVISION_SCALE = 6;

    private final InvestmentRepository investmentRepository;
    private final PersonRepository personRepository;

    public InvestmentResponse create(String personId, InvestmentRequest request) {
        Person person = findPersonOrThrow(personId);
        validateRequest(request);

        Investment investment = new Investment();
        investment.setOwner(person);
        applyRequest(investment, request);
        investment.setProfitability(calculateProfitability(investment.getInitialValue(), investment.getCurrentValue()));

        Investment saved = investmentRepository.save(investment);
        return toResponse(saved);
    }

    public InvestmentSummaryResponse getByPerson(String personId) {
        Person person = findPersonOrThrow(personId);

        List<Investment> investments = investmentRepository.findByOwner(person);

        BigDecimal totalInvested = investments.stream()
                .map(Investment::getInitialValue)
                .filter(v -> v != null)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalCurrent = investments.stream()
                .map(Investment::getCurrentValue)
                .filter(v -> v != null)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalProfitability = calculateProfitability(totalInvested, totalCurrent);

        InvestmentSummaryResponse summary = new InvestmentSummaryResponse();
        summary.setTotalInvested(totalInvested);
        summary.setTotalCurrent(totalCurrent);
        summary.setTotalProfitability(totalProfitability);
        summary.setInvestments(investments.stream().map(this::toResponse).toList());
        return summary;
    }

    public InvestmentResponse findById(String investmentId) {
        UUID uuid = parseUuid(investmentId, "investmentId");
        Investment investment = investmentRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Investimento não encontrado"));
        return toResponse(investment);
    }

    public InvestmentResponse update(String investmentId, InvestmentRequest request) {
        UUID uuid = parseUuid(investmentId, "investmentId");
        Investment investment = investmentRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Investimento não encontrado"));

        validateRequest(request);
        applyRequest(investment, request);
        investment.setProfitability(calculateProfitability(investment.getInitialValue(), investment.getCurrentValue()));

        Investment saved = investmentRepository.save(investment);
        return toResponse(saved);
    }

    public void delete(String investmentId) {
        UUID uuid = parseUuid(investmentId, "investmentId");
        Investment investment = investmentRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Investimento não encontrado"));
        investmentRepository.delete(investment);
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

    private void validateRequest(InvestmentRequest request) {
        if (request == null) throw new BadRequestException("Dados do investimento são obrigatórios");
        if (request.getName() == null || request.getName().isBlank()) throw new BadRequestException("Nome é obrigatório");
        if (request.getType() == null) throw new BadRequestException("Tipo é obrigatório");
        if (request.getInvestmentDate() == null) throw new BadRequestException("Data do investimento é obrigatória");

        if (request.getInitialValue() == null) throw new BadRequestException("Valor inicial é obrigatório");
        if (request.getInitialValue().compareTo(ZERO) <= 0) throw new BadRequestException("Valor inicial deve ser maior que zero");

        if (request.getCurrentValue() == null) throw new BadRequestException("Valor atual é obrigatório");
        if (request.getCurrentValue().compareTo(ZERO) < 0) throw new BadRequestException("Valor atual não pode ser negativo");
    }

    private void applyRequest(Investment investment, InvestmentRequest request) {
        investment.setName(request.getName().trim());
        investment.setType(request.getType());
        investment.setInitialValue(money(request.getInitialValue()));
        investment.setCurrentValue(money(request.getCurrentValue()));
        investment.setInvestmentDate(request.getInvestmentDate());
        investment.setDescription(request.getDescription());
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateProfitability(BigDecimal initial, BigDecimal current) {
        if (initial == null || initial.compareTo(ZERO) <= 0) {
            return ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        if (current == null) {
            current = ZERO;
        }

        return current
                .subtract(initial)
                .divide(initial, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private InvestmentResponse toResponse(Investment investment) {
        InvestmentResponse response = new InvestmentResponse();
        response.setId(investment.getId());
        response.setName(investment.getName());
        response.setType(investment.getType());
        response.setInitialValue(investment.getInitialValue());
        response.setCurrentValue(investment.getCurrentValue());
        response.setInvestmentDate(investment.getInvestmentDate());
        response.setDescription(investment.getDescription());
        response.setProfitability(investment.getProfitability());
        response.setCreatedAt(investment.getCreatedAt());
        response.setUpdatedAt(investment.getUpdatedAt());
        return response;
    }
}
