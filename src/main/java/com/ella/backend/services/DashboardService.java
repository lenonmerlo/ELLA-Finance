package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.dashboard.CategoryBreakdownDTO;
import com.ella.backend.dto.dashboard.CompanyDashboardDTO;
import com.ella.backend.dto.dashboard.ConsolidatedTotalsDTO;
import com.ella.backend.dto.dashboard.DashboardRequestDTO;
import com.ella.backend.dto.dashboard.DashboardResponseDTO;
import com.ella.backend.dto.dashboard.GoalProgressDTO;
import com.ella.backend.dto.dashboard.InvoiceSummaryDTO;
import com.ella.backend.dto.dashboard.MonthlyEvolutionDTO;
import com.ella.backend.dto.dashboard.MonthlyPointDTO;
import com.ella.backend.dto.dashboard.SummaryDTO;
import com.ella.backend.dto.dashboard.TotalsDTO;
import com.ella.backend.entities.Company;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.mappers.FinancialTransactionMapper;
import com.ella.backend.repositories.CompanyRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.repositories.PersonRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final GoalRepository goalRepository;
    private final InvoiceRepository invoiceRepository;
        private final InstallmentRepository installmentRepository;

        @Value("${ella.dashboard.transactions.limit:200}")
        private int personalTransactionsLimit;

    public DashboardResponseDTO buildQuickDashboard(String personId) {
        logger.info("[Dashboard] 🔄 buildQuickDashboard iniciado para personId: {}", personId);

        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        logger.info("[Dashboard] 📅 Buscando dados para ano: {}, mês: {}", year, month);

        DashboardRequestDTO request = DashboardRequestDTO.builder()
                .personId(personId)
                .year(year)
                .month(month)
                .build();

        DashboardResponseDTO response = buildDashboard(request);

        logger.info("[Dashboard] ✅ buildQuickDashboard concluído. Transações retornadas: {}",
                response.getPersonalTransactions() != null ? response.getPersonalTransactions().size() : 0);

        return response;
    }

    @Cacheable(
            cacheNames = "dashboard",
            key = "#request.personId + '-' + #request.year + '-' + #request.month",
            sync = true
    )
    public DashboardResponseDTO buildDashboard(DashboardRequestDTO request) {
        logger.info("[Dashboard] 🔄 buildDashboard iniciado para personId: {}, year: {}, month: {}",
                request.getPersonId(), request.getYear(), request.getMonth());

        UUID personUuid = UUID.fromString(request.getPersonId());
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada"));

        int year = request.getYear();
        int month = request.getMonth();

        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        LocalDate yearStart = YearMonth.of(year, 1).atDay(1);
        LocalDate yearEnd = YearMonth.of(year, 12).atEndOfMonth();

        logger.debug("[Dashboard] 📅 Período do mês: {} a {}", monthStart, monthEnd);
        logger.debug("[Dashboard] 📅 Período do ano: {} a {}", yearStart, yearEnd);

        // Performance: não carregue todas as transações do mês/ano.
        // Use agregações no banco + uma lista limitada para a UI.
        BigDecimal monthIncome = financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(
                person, monthStart, monthEnd, TransactionType.INCOME);
        BigDecimal monthExpenses = financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(
                person, monthStart, monthEnd, TransactionType.EXPENSE);

        BigDecimal yearIncome = financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(
                person, yearStart, yearEnd, TransactionType.INCOME);
        BigDecimal yearExpenses = financialTransactionRepository.sumAmountByPersonAndDateRangeAndType(
                person, yearStart, yearEnd, TransactionType.EXPENSE);

        List<FinancialTransactionRepository.CategoryTotalProjection> monthExpenseByCategory =
                financialTransactionRepository.sumExpenseTotalsByCategoryForPersonAndDateRange(
                        person, monthStart, monthEnd);

        List<FinancialTransactionRepository.MonthTypeTotalProjection> yearMonthlyTotals =
                financialTransactionRepository.sumTotalsByMonthAndTypeForPersonAndDateRange(
                        person.getId(), yearStart, yearEnd);

        List<Invoice> personalInvoicesEntities =
                invoiceRepository.findByCardOwnerAndMonthAndYearAndDeletedAtIsNull(person, month, year);
        logger.info("[Dashboard] 🧾 Faturas encontradas: {}", personalInvoicesEntities.size());

        List<Goal> personalGoals = goalRepository.findByOwner(person);
        logger.info("[Dashboard] 🎯 Metas encontradas: {}", personalGoals.size());

        List<Company> companies = companyRepository.findByOwner(person);
        logger.info("[Dashboard] 🏢 Empresas encontradas: {}", companies.size());

        SummaryDTO personalSummary = SummaryDTO.builder()
                .totalIncome(monthIncome)
                .totalExpenses(monthExpenses)
                .balance(monthIncome.subtract(monthExpenses))
                .build();

        TotalsDTO personalTotals = TotalsDTO.builder()
                .monthIncome(monthIncome)
                .monthExpenses(monthExpenses)
                .yearIncome(yearIncome)
                .yearExpenses(yearExpenses)
                .build();

        List<CategoryBreakdownDTO> personalCategory = buildCategoryBreakdownFromAggregates(monthExpenseByCategory);
        MonthlyEvolutionDTO personalMonthlyEvolution = buildMonthlyEvolutionFromAggregates(yearMonthlyTotals, year);
        GoalProgressDTO mainGoalProgress = buildMainGoalProgress(personalGoals);
        List<InvoiceSummaryDTO> personalInvoices = buildInvoiceSummaries(personalInvoicesEntities);

        List<FinancialTransactionResponseDTO> personalTransactions = loadLimitedTransactions(person, yearStart, monthEnd);
        logger.info("[Dashboard] ✅ Transações retornadas (limitadas): {}", personalTransactions.size());

        List<CompanyDashboardDTO> companyDashboards = buildCompanyDashboards(companies, year, month);

        ConsolidatedTotalsDTO consolidated = buildConsolidatedTotals(personalTotals, companyDashboards);

        DashboardResponseDTO response = DashboardResponseDTO.builder()
                .personId(request.getPersonId())
                .personalSummary(personalSummary)
                .personalTotals(personalTotals)
                .personalCategoryBreakdown(personalCategory)
                .personalMonthlyEvolution(personalMonthlyEvolution)
                .goalProgress(mainGoalProgress)
                .personalInvoices(personalInvoices)
                .personalTransactions(personalTransactions) // ✅ Agora retorna transações do ano
                .companies(companyDashboards)
                .consolidatedTotals(consolidated)
                .build();

        logger.info("[Dashboard] ✅ buildDashboard concluído com sucesso");

        return response;
    }

        private List<FinancialTransactionResponseDTO> loadLimitedTransactions(Person person, LocalDate startDate, LocalDate endDate) {
                int limit = Math.max(0, personalTransactionsLimit);
                // Guard rail: evita payloads enormes por configuração acidental.
                limit = Math.min(limit, 500);
                if (limit == 0) {
                        return List.of();
                }

                var page = financialTransactionRepository.findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(
                                person,
                                startDate,
                                endDate,
                                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "transactionDate"))
                );

                List<FinancialTransactionResponseDTO> list = page.getContent().stream()
                                .filter(Objects::nonNull)
                                .map(FinancialTransactionMapper::toResponseDTO)
                                .sorted(Comparator.comparing(FinancialTransactionResponseDTO::transactionDate).reversed())
                                .collect(Collectors.toList());

                if (list.size() <= limit) {
                        return list;
                }
                return list.subList(0, limit);
        }

        private List<CategoryBreakdownDTO> buildCategoryBreakdownFromAggregates(
                        List<FinancialTransactionRepository.CategoryTotalProjection> monthExpenseByCategory
        ) {
                if (monthExpenseByCategory == null || monthExpenseByCategory.isEmpty()) {
                        return Collections.emptyList();
                }

                BigDecimal totalExpenses = monthExpenseByCategory.stream()
                                .map(FinancialTransactionRepository.CategoryTotalProjection::getTotal)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
                        return Collections.emptyList();
                }

                return monthExpenseByCategory.stream()
                                .filter(p -> p != null && p.getCategory() != null && p.getTotal() != null)
                                .map(p -> {
                                        BigDecimal percentage = p.getTotal()
                                                        .multiply(BigDecimal.valueOf(100))
                                                        .divide(totalExpenses, 2, RoundingMode.HALF_UP);
                                        return CategoryBreakdownDTO.builder()
                                                        .category(p.getCategory())
                                                        .total(p.getTotal())
                                                        .percentage(percentage)
                                                        .build();
                                })
                                .sorted(Comparator.comparing(CategoryBreakdownDTO::getTotal).reversed())
                                .toList();
        }

        private MonthlyEvolutionDTO buildMonthlyEvolutionFromAggregates(
                        List<FinancialTransactionRepository.MonthTypeTotalProjection> yearMonthlyTotals,
                        int year
        ) {
                Map<YearMonth, EnumMap<TransactionType, BigDecimal>> map = new HashMap<>();

                if (yearMonthlyTotals != null) {
                        for (var row : yearMonthlyTotals) {
                                if (row == null || row.getMonthStart() == null || row.getType() == null || row.getTotal() == null) {
                                        continue;
                                }

                                TransactionType type;
                                try {
                                        type = TransactionType.valueOf(row.getType());
                                } catch (Exception ignored) {
                                        continue;
                                }

                                YearMonth ym = YearMonth.from(row.getMonthStart());
                                EnumMap<TransactionType, BigDecimal> totals = map.computeIfAbsent(ym, k -> new EnumMap<>(TransactionType.class));
                                totals.put(type, row.getTotal());
                        }
                }

                List<MonthlyPointDTO> points = new ArrayList<>();
                for (int m = 1; m <= 12; m++) {
                        YearMonth ym = YearMonth.of(year, m);
                        EnumMap<TransactionType, BigDecimal> totals = map.getOrDefault(ym, new EnumMap<>(TransactionType.class));

                        BigDecimal income = totals.getOrDefault(TransactionType.INCOME, BigDecimal.ZERO);
                        BigDecimal expenses = totals.getOrDefault(TransactionType.EXPENSE, BigDecimal.ZERO);

                        String label = String.format("%04d-%02d", year, m);
                        points.add(MonthlyPointDTO.builder()
                                        .monthLabel(label)
                                        .income(income)
                                        .expenses(expenses)
                                        .build());
                }

                return MonthlyEvolutionDTO.builder()
                                .points(points)
                                .build();
        }

    // ================== BLOCO PESSOAL ==================

    private SummaryDTO buildSummary(List<FinancialTransaction> monthTx) {
        BigDecimal income = sumByType(monthTx, TransactionType.INCOME);
        BigDecimal expenses = sumByType(monthTx, TransactionType.EXPENSE);

        return SummaryDTO.builder()
                .totalIncome(income)
                .totalExpenses(expenses)
                .balance(income.subtract(expenses))
                .build();
    }

    private TotalsDTO buildTotals(List<FinancialTransaction> monthTx, List<FinancialTransaction> yearTx) {
        BigDecimal monthIncome = sumByType(monthTx, TransactionType.INCOME);
        BigDecimal monthExpenses = sumByType(monthTx, TransactionType.EXPENSE);
        BigDecimal yearIncome = sumByType(yearTx, TransactionType.INCOME);
        BigDecimal yearExpenses = sumByType(yearTx, TransactionType.EXPENSE);

        return TotalsDTO.builder()
                .monthIncome(monthIncome)
                .monthExpenses(monthExpenses)
                .yearIncome(yearIncome)
                .yearExpenses(yearExpenses)
                .build();
    }

    private List<CategoryBreakdownDTO> buildCategoryBreakdown(List<FinancialTransaction> monthTx) {
        // Considera apenas DESPESAS
        List<FinancialTransaction> expensesTx = monthTx.stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .toList();

        BigDecimal totalExpenses = expensesTx.stream()
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            return Collections.emptyList();
        }

        Map<String, BigDecimal> byCategory = expensesTx.stream()
                .collect(Collectors.groupingBy(
                        FinancialTransaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, FinancialTransaction::getAmount, BigDecimal::add)
                ));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    BigDecimal total = entry.getValue();
                    BigDecimal percentage = total
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalExpenses, 2, RoundingMode.HALF_UP);

                    return CategoryBreakdownDTO.builder()
                            .category(entry.getKey())
                            .total(total)  // ✅ CORRIGIDO: total ao invés de amount
                            .percentage(percentage)
                            .build();
                })
                .sorted(Comparator.comparing(CategoryBreakdownDTO::getTotal).reversed())
                .toList();
    }

    private MonthlyEvolutionDTO buildMonthlyEvolution(List<FinancialTransaction> yearTx, int year) {
        Map<YearMonth, List<FinancialTransaction>> grouped = yearTx.stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .collect(Collectors.groupingBy(
                        tx -> YearMonth.from(tx.getTransactionDate())
                ));

        List<MonthlyPointDTO> points = new ArrayList<>();

        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            List<FinancialTransaction> txs = grouped.getOrDefault(ym, Collections.emptyList());

            BigDecimal income = sumByType(txs, TransactionType.INCOME);
            BigDecimal expenses = sumByType(txs, TransactionType.EXPENSE);

            String label = String.format("%04d-%02d", year, m);

            points.add(MonthlyPointDTO.builder()
                    .monthLabel(label)
                    .income(income)
                    .expenses(expenses)
                    .build());
        }

        return MonthlyEvolutionDTO.builder()
                .points(points)
                .build();
    }

    private GoalProgressDTO buildMainGoalProgress(List<Goal> personalGoals) {
        if (personalGoals == null || personalGoals.isEmpty()) {
            return null;
        }

        // Estratégia simples: pega o primeiro objetivo ACTIVE; se não tiver, pega o primeiro da lista
        Goal goal = personalGoals.stream()
                .filter(g -> g.getStatus() != null)
                .findFirst()
                .orElse(personalGoals.get(0));

        BigDecimal target = goal.getTargetAmount() != null ? goal.getTargetAmount() : BigDecimal.ZERO;
        BigDecimal current = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;

        BigDecimal percentage = BigDecimal.ZERO;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            percentage = current
                    .multiply(BigDecimal.valueOf(100))
                    .divide(target, 2, RoundingMode.HALF_UP);
        }

        return GoalProgressDTO.builder()
                .goalId(goal.getId().toString())
                .title(goal.getTitle())
                .targetAmount(target)
                .currentAmount(current)
                .percentage(percentage)  // ✅ CORRIGIDO: percentage (BigDecimal) ao invés de int
                .status(goal.getStatus())
                .build();
    }

    private List<InvoiceSummaryDTO> buildInvoiceSummaries(List<Invoice> invoices) {
        LocalDate today = LocalDate.now();

        return invoices.stream()
                .map(inv -> {
                    boolean isOverdue = inv.getStatus() != InvoiceStatus.PAID
                            && inv.getDueDate() != null
                            && inv.getDueDate().isBefore(today);

                    boolean isPaid = inv.getStatus() == InvoiceStatus.PAID;

                    String holderName = inv.getCard() != null ? inv.getCard().getCardholderName() : null;
                    if (holderName == null || holderName.isBlank()) {
                        holderName = inv.getCard() != null && inv.getCard().getOwner() != null
                                ? inv.getCard().getOwner().getName()
                                : "";
                    }

                    return InvoiceSummaryDTO.builder()
                            .invoiceId(inv.getId().toString())
                            .creditCardId(inv.getCard().getId().toString())
                            .creditCardName(inv.getCard().getName())
                            .creditCardBrand(inv.getCard().getBrand())
                            .creditCardLastFourDigits(inv.getCard().getLastFourDigits())
                            .personName(holderName)
                                                        .totalAmount(calculateInvoiceNetTotal(inv))
                            .dueDate(inv.getDueDate())
                            .isOverdue(isOverdue)
                            .isPaid(isPaid)
                            .paidDate(inv.getPaidDate())
                            .build();
                })
                .toList();
    }

        private BigDecimal calculateInvoiceNetTotal(Invoice invoice) {
                if (invoice == null) return BigDecimal.ZERO;
                try {
                        return installmentRepository.findByInvoice(invoice).stream()
                                        .filter(inst -> inst != null && inst.getTransaction() != null)
                                        .map(inst -> {
                                                BigDecimal amount = inst.getAmount();
                                                if (amount == null) return BigDecimal.ZERO;
                                                // Match invoice total logic: expenses add, everything else subtract.
                                                return inst.getTransaction().getType() == TransactionType.EXPENSE
                                                                ? amount
                                                                : amount.negate();
                                        })
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                } catch (Exception e) {
                        return invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
                }
        }

    // ================== BLOCO EMPRESAS ==================

    private List<CompanyDashboardDTO> buildCompanyDashboards(
            List<Company> companies,
            int year,
            int month
    ) {
        if (companies == null || companies.isEmpty()) {
            return Collections.emptyList();
        }

        return companies.stream()
                .map(company -> CompanyDashboardDTO.builder()
                        .companyId(company.getId().toString())
                        .companyName(company.getName())
                        .summary(SummaryDTO.builder()
                                .totalIncome(BigDecimal.ZERO)
                                .totalExpenses(BigDecimal.ZERO)
                                .balance(BigDecimal.ZERO)
                                .build())
                        .totals(TotalsDTO.builder()
                                .monthIncome(BigDecimal.ZERO)
                                .monthExpenses(BigDecimal.ZERO)
                                .yearIncome(BigDecimal.ZERO)
                                .yearExpenses(BigDecimal.ZERO)
                                .build())
                        .categoryBreakdown(Collections.emptyList())
                        .invoices(Collections.emptyList())
                        .build())
                .toList();
    }

    // ================== CONSOLIDADO ==================

    private ConsolidatedTotalsDTO buildConsolidatedTotals(
            TotalsDTO personalTotals,
            List<CompanyDashboardDTO> companyDashboards
    ) {
        BigDecimal totalBalance = personalTotals.getMonthIncome().subtract(personalTotals.getMonthExpenses());
        BigDecimal totalIncome = personalTotals.getMonthIncome();
        BigDecimal totalExpense = personalTotals.getMonthExpenses();

        for (CompanyDashboardDTO comp : companyDashboards) {
            totalIncome = totalIncome.add(comp.getTotals().getMonthIncome());
            totalExpense = totalExpense.add(comp.getTotals().getMonthExpenses());
        }

        totalBalance = totalIncome.subtract(totalExpense);

        return ConsolidatedTotalsDTO.builder()
                .balance(totalBalance)
                .totalIncome(totalIncome)
                .totalExpenses(totalExpense)
                .build();
    }

    // ================== HELPERS ==================

    private BigDecimal sumByType(List<FinancialTransaction> txs, TransactionType type) {
        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}