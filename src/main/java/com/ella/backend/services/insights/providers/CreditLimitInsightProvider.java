package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(50)
public class CreditLimitInsightProvider implements InsightProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InsightDataCache insightDataCache;
    private final CreditCardRepository creditCardRepository;

    public CreditLimitInsightProvider(InsightDataCache insightDataCache, CreditCardRepository creditCardRepository) {
        this.insightDataCache = insightDataCache;
        this.creditCardRepository = creditCardRepository;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<FinancialTransaction> txs = insightDataCache.getTransactionsForMonth(person, ym);
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }

        BigDecimal monthlyExpenses = txs.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .map(InsightUtils::safeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditLimit = creditCardRepository.findByOwner(person).stream()
                .map(CreditCard::getLimitAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal utilization = monthlyExpenses
                .divide(creditLimit, 2, RoundingMode.HALF_UP)
                .multiply(HUNDRED);

        if (utilization.compareTo(BigDecimal.valueOf(80)) <= 0) {
            return List.of();
        }

        return List.of(InsightDTO.builder()
                .type("warning")
                .message(String.format("Você já utilizou %.0f%% do seu limite de crédito", utilization))
                .category("Limite")
                .build());
    }
}
