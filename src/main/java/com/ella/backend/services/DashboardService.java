package com.ella.backend.services;

import com.ella.backend.dto.dashboard.*;
import com.ella.backend.entities.*;
import com.ella.backend.enums.InvoiceStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final GoalRepository goalRepository;
    private final InvoiceRepository invoiceRepository;

    public DashboardResponseDTO buildQuickDashboard(String personId) {

        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(personUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        DashboardRequestDTO request = DashboardRequestDTO.builder()
                .personId(personId)
                .year(year)
                .month(month)
                .build();

        return buildDashboard(request);
    }

    public DashboardResponseDTO buildDashboard(DashboardRequestDTO request) {
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

        List<FinancialTransaction> personalMonthTx =
                financialTransactionRepository.findByPersonAndTransactionDateBetween(
                        person, monthStart, monthEnd
                );

        List<FinancialTransaction> personalYearTx =
                financialTransactionRepository.findByPersonAndTransactionDateBetween(
                        person, yearStart, yearEnd
                );

        List<Invoice> personalInvoicesEntities =
                invoiceRepository.findByCardOwnerAndMonthAndYear(person, month, year);

        List<Goal> personalGoals = goalRepository.findByOwner(person);

        List<Company> companies = companyRepository.findByOwner(person);

        SummaryDTO personalSummary = buildSummary(personalMonthTx);
        TotalsDTO personalTotals = buildTotals(personalMonthTx, personalYearTx);
        List<CategoryBreakdownDTO> personalCategory = buildCategoryBreakdown(personalMonthTx);
        MonthlyEvolutionDTO personalMonthlyEvolution = buildMonthlyEvolution(personalYearTx, year);
        GoalProgressDTO mainGoalProgress = buildMainGoalProgress(personalGoals);
        List<InvoiceSummaryDTO> personalInvoices = buildInvoiceSummaries(personalInvoicesEntities);

        List<CompanyDashboardDTO> companyDashboards = buildCompanyDashboards(companies, year, month);

        ConsolidatedTotalsDTO consolidated = buildConsolidatedTotals(personalTotals, companyDashboards);

        return DashboardResponseDTO.builder()
                .personId(request.getPersonId())
                .personalSummary(personalSummary)
                .personalTotals(personalTotals)
                .personalCategoryBreakdown(personalCategory)
                .personalMonthlyEvolution(personalMonthlyEvolution)
                .goalProgress(mainGoalProgress)
                .personalInvoices(personalInvoices)
                .companies(companyDashboards)
                .consolidatedTotals(consolidated)
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
                            .total(total)
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
                .percentage(percentage)
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

                    return InvoiceSummaryDTO.builder()
                            .creditCardId(inv.getCard().getId().toString())
                            .creditCardName(inv.getCard().getName())
                            .totalAmount(inv.getTotalAmount())
                            .dueDate(inv.getDueDate())
                            .isOverdue(isOverdue)
                            .build();
                })
                .toList();
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

        // ⚠️ IMPORTANTE:
        // Neste momento, a entidade FinancialTransaction ainda não está ligada a Company.
        // Então, por enquanto, vamos retornar empresas com valores zerados / placeholders.
        // Quando você ligar transações à Company, aqui vira o mirror da lógica pessoal.

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
                        .monthlyEvolution(MonthlyEvolutionDTO.builder()
                                .points(Collections.emptyList())
                                .build())
                        .invoices(Collections.emptyList())
                        .build())
                .toList();
    }

    // ================== CONSOLIDADO ==================

    private ConsolidatedTotalsDTO buildConsolidatedTotals(
            TotalsDTO personalTotals,
            List<CompanyDashboardDTO> companies
    ) {
        BigDecimal personalIncome = personalTotals != null ? personalTotals.getYearIncome() : BigDecimal.ZERO;
        BigDecimal personalExpenses = personalTotals != null ? personalTotals.getYearExpenses() : BigDecimal.ZERO;

        BigDecimal companiesIncome = companies.stream()
                .map(c -> c.getTotals() != null ? c.getTotals().getYearIncome() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal companiesExpenses = companies.stream()
                .map(c -> c.getTotals() != null ? c.getTotals().getYearExpenses() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncome = personalIncome.add(companiesIncome);
        BigDecimal totalExpenses = personalExpenses.add(companiesExpenses);

        return ConsolidatedTotalsDTO.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .balance(totalIncome.subtract(totalExpenses))
                .build();
    }

    // ================== UTIL ==================

    private BigDecimal sumByType(List<FinancialTransaction> txs, TransactionType type) {
        if (txs == null || txs.isEmpty()) return BigDecimal.ZERO;

        return txs.stream()
                .filter(tx -> tx.getType() == type)
                .map(FinancialTransaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


}
