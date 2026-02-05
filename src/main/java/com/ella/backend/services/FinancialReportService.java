package com.ella.backend.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.dto.reports.CategoryTotalDTO;
import com.ella.backend.dto.reports.GenerateReportRequestDTO;
import com.ella.backend.dto.reports.ReportListItemDTO;
import com.ella.backend.dto.reports.ReportResponseDTO;
import com.ella.backend.dto.reports.ReportSummaryDTO;
import com.ella.backend.entities.Asset;
import com.ella.backend.entities.BankStatement;
import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.entities.Budget;
import com.ella.backend.entities.FinancialReport;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Goal;
import com.ella.backend.entities.Investment;
import com.ella.backend.entities.Person;
import com.ella.backend.enums.ReportType;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.AssetRepository;
import com.ella.backend.repositories.BankStatementTransactionRepository;
import com.ella.backend.repositories.BudgetRepository;
import com.ella.backend.repositories.FinancialReportRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.GoalRepository;
import com.ella.backend.repositories.InvestmentRepository;
import com.ella.backend.repositories.PersonRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final FinancialReportRepository financialReportRepository;
    private final PersonRepository personRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final InvestmentRepository investmentRepository;
    private final AssetRepository assetRepository;
    private final GoalRepository goalRepository;
    private final BudgetRepository budgetRepository;
    private final BankStatementTransactionRepository bankStatementTransactionRepository;
    private final EntityManager entityManager;
    private final DashboardInsightsService dashboardInsightsService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReportResponseDTO generate(String personId, GenerateReportRequestDTO request) {
        UUID personUuid = UUID.fromString(personId);
        Person person = personRepository.findById(Objects.requireNonNull(personUuid))
                .orElseThrow(() -> new EntityNotFoundException("Person not found"));

        ReportType type = request.getType();
        LocalDate referenceDate = LocalDate.of(request.getYear(), request.getMonth(), 1);
        Period period = resolvePeriod(type, referenceDate);
        Period prevPeriod = resolvePreviousPeriod(type, period);

        List<FinancialTransaction> tx = financialTransactionRepository
            .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, period.start, period.end);
        List<FinancialTransaction> prevTx = financialTransactionRepository
            .findByPersonAndTransactionDateBetweenAndDeletedAtIsNull(person, prevPeriod.start, prevPeriod.end);

        ComputedSummary currentSummary = computeSummary(tx);
        ComputedSummary previousSummary = computeSummary(prevTx);

        ReportSummaryDTO summary = buildSummary(currentSummary, previousSummary);

        List<CategoryTotalDTO> expensesByCategory = computeByCategory(tx, TransactionType.EXPENSE);
        List<CategoryTotalDTO> incomesByCategory = computeByCategory(tx, TransactionType.INCOME);

        Map<String, Object> investments = computeInvestments(person);
        Map<String, Object> assets = computeAssets(person);
        Map<String, Object> goals = computeGoals(person);
        Map<String, Object> budget = computeBudget(person);
        Map<String, Object> bankStatements = computeBankStatements(person.getId(), period.start, period.end);

        List<Map<String, Object>> insights = computeInsights(personId, period.end);

        String title = buildTitle(type, referenceDate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", objectMapper.convertValue(summary, new TypeReference<Map<String, Object>>() {}));
        data.put("expensesByCategory", objectMapper.convertValue(expensesByCategory, new TypeReference<List<Map<String, Object>>>() {}));
        data.put("incomesByCategory", objectMapper.convertValue(incomesByCategory, new TypeReference<List<Map<String, Object>>>() {}));
        data.put("investments", investments);
        data.put("assets", assets);
        data.put("goals", goals);
        data.put("budget", budget);
        data.put("bankStatements", bankStatements);
        data.put("insights", insights);

        FinancialReport saved = financialReportRepository.save(Objects.requireNonNull(FinancialReport.builder()
                .person(person)
                .reportType(type)
                .title(title)
                .periodStart(period.start)
                .periodEnd(period.end)
                .referenceDate(referenceDate)
                .data(data)
            .build()));

        return toResponseDTO(saved, summary, expensesByCategory, incomesByCategory, investments, assets, goals, budget, bankStatements, insights);
    }

    @Transactional(readOnly = true)
    public Page<ReportListItemDTO> list(String personId, int page, int size) {
        UUID personUuid = UUID.fromString(personId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return financialReportRepository.findByPersonIdOrderByCreatedAtDesc(personUuid, pageable)
                .map(r -> ReportListItemDTO.builder()
                        .id(r.getId())
                        .type(r.getReportType())
                        .title(r.getTitle())
                        .periodStart(r.getPeriodStart())
                        .periodEnd(r.getPeriodEnd())
                        .createdAt(r.getCreatedAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public ReportResponseDTO get(String personId, String reportId) {
        UUID personUuid = UUID.fromString(personId);
        UUID reportUuid = UUID.fromString(reportId);

        FinancialReport report = financialReportRepository.findByIdAndPersonId(reportUuid, personUuid)
                .orElseThrow(() -> new EntityNotFoundException("Report not found"));

        Map<String, Object> data = report.getData() == null ? Map.of() : report.getData();

        ReportSummaryDTO summary = objectMapper.convertValue(
            data.getOrDefault("summary", Map.of()),
            ReportSummaryDTO.class
        );

        List<CategoryTotalDTO> expenses = objectMapper.convertValue(
            data.getOrDefault("expensesByCategory", List.of()),
            new TypeReference<List<CategoryTotalDTO>>() {}
        );

        List<CategoryTotalDTO> incomes = objectMapper.convertValue(
            data.getOrDefault("incomesByCategory", List.of()),
            new TypeReference<List<CategoryTotalDTO>>() {}
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> investments = (Map<String, Object>) data.getOrDefault("investments", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> assets = (Map<String, Object>) data.getOrDefault("assets", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> goals = (Map<String, Object>) data.getOrDefault("goals", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> budget = (Map<String, Object>) data.getOrDefault("budget", Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> bankStatements = (Map<String, Object>) data.getOrDefault("bankStatements", Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> insights = (List<Map<String, Object>>) data.getOrDefault("insights", List.of());

        return toResponseDTO(report, summary, expenses, incomes, investments, assets, goals, budget, bankStatements, insights);
    }

    public byte[] renderPdf(String personId, String reportId) {
        ReportResponseDTO report = get(personId, reportId);
        String html = FinancialReportTemplates.toPdfHtml(report);
        return PdfRenderer.render(html);
    }

    private static ReportResponseDTO toResponseDTO(
            FinancialReport report,
            ReportSummaryDTO summary,
            List<CategoryTotalDTO> expensesByCategory,
            List<CategoryTotalDTO> incomesByCategory,
            Map<String, Object> investments,
            Map<String, Object> assets,
            Map<String, Object> goals,
            Map<String, Object> budget,
            Map<String, Object> bankStatements,
            List<Map<String, Object>> insights
    ) {
        return ReportResponseDTO.builder()
                .id(report.getId())
                .personId(report.getPerson().getId())
                .type(report.getReportType())
                .title(report.getTitle())
                .periodStart(report.getPeriodStart())
                .periodEnd(report.getPeriodEnd())
                .referenceDate(report.getReferenceDate())
                .createdAt(report.getCreatedAt())
                .summary(summary)
                .expensesByCategory(expensesByCategory)
                .incomesByCategory(incomesByCategory)
                .investments(investments)
                .assets(assets)
                .goals(goals)
                .budget(budget)
                .bankStatements(bankStatements)
                .insights(insights)
                .build();
    }

    private static List<CategoryTotalDTO> computeByCategory(List<FinancialTransaction> tx, TransactionType type) {
        Map<String, BigDecimal> totals = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (FinancialTransaction t : tx) {
            if (t.getType() != type) continue;
            BigDecimal abs = t.getAmount() == null ? BigDecimal.ZERO : t.getAmount().abs();
            totals.merge(String.valueOf(t.getCategory()), abs, BigDecimal::add);
            total = total.add(abs);
        }

        List<CategoryTotalDTO> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : totals.entrySet()) {
            BigDecimal percent = total.signum() == 0
                    ? BigDecimal.ZERO
                    : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
            list.add(CategoryTotalDTO.builder()
                    .category(e.getKey())
                    .amount(e.getValue())
                    .percent(percent)
                    .build());
        }

        list.sort(Comparator.comparing(CategoryTotalDTO::getAmount).reversed());
        return list;
    }

    private static ComputedSummary computeSummary(List<FinancialTransaction> tx) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (FinancialTransaction t : tx) {
            BigDecimal abs = t.getAmount() == null ? BigDecimal.ZERO : t.getAmount().abs();
            if (t.getType() == TransactionType.INCOME) {
                income = income.add(abs);
            } else if (t.getType() == TransactionType.EXPENSE) {
                expenses = expenses.add(abs);
            }
        }

        BigDecimal balance = income.subtract(expenses);
        BigDecimal savingsRate = income.signum() == 0
                ? BigDecimal.ZERO
                : balance.multiply(BigDecimal.valueOf(100)).divide(income, 2, RoundingMode.HALF_UP);

        return new ComputedSummary(income, expenses, balance, savingsRate);
    }

    private static ReportSummaryDTO buildSummary(ComputedSummary current, ComputedSummary previous) {
        return ReportSummaryDTO.builder()
                .totalIncome(current.income)
                .totalExpenses(current.expenses)
                .balance(current.balance)
                .savingsRate(current.savingsRate)
                .prevTotalIncome(previous.income)
                .prevTotalExpenses(previous.expenses)
                .prevBalance(previous.balance)
                .incomeChange(current.income.subtract(previous.income))
                .expensesChange(current.expenses.subtract(previous.expenses))
                .balanceChange(current.balance.subtract(previous.balance))
                .build();
    }

    private Map<String, Object> computeInvestments(Person person) {
        List<Investment> investments = investmentRepository.findByOwner(person);
        BigDecimal initial = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        Map<String, BigDecimal> byType = new HashMap<>();

        for (Investment inv : investments) {
            BigDecimal iv = inv.getInitialValue() == null ? BigDecimal.ZERO : inv.getInitialValue();
            BigDecimal cv = inv.getCurrentValue() == null ? BigDecimal.ZERO : inv.getCurrentValue();
            initial = initial.add(iv);
            current = current.add(cv);
            byType.merge(String.valueOf(inv.getType()), cv, BigDecimal::add);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", investments.size());
        out.put("totalInitialValue", initial);
        out.put("totalCurrentValue", current);
        out.put("totalChange", current.subtract(initial));
        out.put("byType", byType);
        return out;
    }

    private Map<String, Object> computeAssets(Person person) {
        List<Asset> assets = assetRepository.findByOwner(person);
        BigDecimal current = BigDecimal.ZERO;
        Map<String, BigDecimal> byType = new HashMap<>();

        for (Asset a : assets) {
            BigDecimal cv = a.getCurrentValue() == null ? BigDecimal.ZERO : a.getCurrentValue();
            current = current.add(cv);
            byType.merge(String.valueOf(a.getType()), cv, BigDecimal::add);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", assets.size());
        out.put("totalCurrentValue", current);
        out.put("byType", byType);
        return out;
    }

    private Map<String, Object> computeGoals(Person person) {
        List<Goal> goals = goalRepository.findByOwner(person);
        Map<String, Long> byStatus = new HashMap<>();
        BigDecimal target = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;

        for (Goal g : goals) {
            byStatus.merge(String.valueOf(g.getStatus()), 1L, (a, b) -> a + b);
            target = target.add(g.getTargetAmount() == null ? BigDecimal.ZERO : g.getTargetAmount());
            current = current.add(g.getCurrentAmount() == null ? BigDecimal.ZERO : g.getCurrentAmount());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", goals.size());
        out.put("byStatus", byStatus);
        out.put("totalTargetAmount", target);
        out.put("totalCurrentAmount", current);
        return out;
    }

    private Map<String, Object> computeBudget(Person person) {
        return budgetRepository.findByOwner(person)
                .map(this::mapBudget)
                .orElseGet(() -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("configured", false);
                    return out;
                });
    }

    private Map<String, Object> mapBudget(Budget b) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", true);
        out.put("income", b.getIncome());
        out.put("essentialFixedCost", b.getEssentialFixedCost());
        out.put("necessaryFixedCost", b.getNecessaryFixedCost());
        out.put("variableFixedCost", b.getVariableFixedCost());
        out.put("investment", b.getInvestment());
        out.put("plannedPurchase", b.getPlannedPurchase());
        out.put("protection", b.getProtection());
        out.put("total", b.getTotal());
        out.put("balance", b.getBalance());
        out.put("necessitiesPercentage", b.getNecessitiesPercentage());
        out.put("desiresPercentage", b.getDesiresPercentage());
        out.put("investmentsPercentage", b.getInvestmentsPercentage());
        out.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
        out.put("updatedAt", b.getUpdatedAt() != null ? b.getUpdatedAt().toString() : null);
        return out;
    }

    private Map<String, Object> computeBankStatements(UUID userId, LocalDate start, LocalDate end) {
        List<BankStatementTransaction> txs = bankStatementTransactionRepository.findForUserAndPeriod(userId, start, end);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (BankStatementTransaction t : txs) {
            if (t == null || t.getAmount() == null) continue;
            if (t.getType() == BankStatementTransaction.Type.BALANCE) continue;
            BigDecimal abs = t.getAmount().abs();
            if (t.getType() == BankStatementTransaction.Type.CREDIT) income = income.add(abs);
            else expenses = expenses.add(abs);
        }

        BankStatement opening = findStatement(userId, start, end, true);
        BankStatement closing = findStatement(userId, start, end, false);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openingBalance", opening != null ? opening.getOpeningBalance() : null);
        summary.put("closingBalance", closing != null ? closing.getClosingBalance() : null);
        summary.put("totalIncome", income);
        summary.put("totalExpenses", expenses);
        summary.put("balance", income.subtract(expenses));
        summary.put("transactionCount", txs.size());

        int limit = 200;
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (int i = 0; i < txs.size() && i < limit; i++) {
            BankStatementTransaction t = txs.get(i);
            if (t == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId() != null ? t.getId().toString() : "");
            m.put("transactionDate", t.getTransactionDate() != null ? t.getTransactionDate().toString() : null);
            m.put("description", t.getDescription());
            m.put("amount", t.getAmount());
            m.put("balance", t.getBalance());
            m.put("type", t.getType() != null ? t.getType().name() : null);
            mapped.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", summary);
        out.put("transactions", mapped);
        out.put("totalTransactions", txs.size());
        out.put("limitedTo", limit);
        return out;
    }

    private BankStatement findStatement(UUID userId, LocalDate startDate, LocalDate endDate, boolean earliest) {
        String order = earliest ? "asc" : "desc";
        return entityManager
                .createQuery(
                        "select b from BankStatement b "
                                + "where b.userId = :userId "
                                + "and b.statementDate >= :startDate and b.statementDate <= :endDate "
                                + "order by b.statementDate " + order,
                        BankStatement.class)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<Map<String, Object>> computeInsights(String personId, LocalDate periodEnd) {
        int year = periodEnd.getYear();
        int month = periodEnd.getMonthValue();
        List<InsightDTO> list = dashboardInsightsService.getInsights(personId, year, month);
        List<Map<String, Object>> out = new ArrayList<>();
        for (InsightDTO i : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", i.getType());
            m.put("category", i.getCategory());
            m.put("message", i.getMessage());
            out.add(m);
        }
        return out;
    }

    private static String buildTitle(ReportType type, LocalDate referenceDate) {
        Locale ptBR = Locale.forLanguageTag("pt-BR");
        if (type == ReportType.MONTHLY) {
            String monthName = referenceDate.getMonth().getDisplayName(TextStyle.FULL, ptBR);
            return "Relatório Mensal – " + capitalize(monthName) + " " + referenceDate.getYear();
        }
        if (type == ReportType.SEMIANNUAL) {
            int sem = referenceDate.getMonthValue() <= 6 ? 1 : 2;
            return "Relatório Semestral – " + sem + "º semestre " + referenceDate.getYear();
        }
        return "Relatório Anual – " + referenceDate.getYear();
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static Period resolvePeriod(ReportType type, LocalDate referenceDate) {
        if (type == ReportType.MONTHLY) {
            YearMonth ym = YearMonth.of(referenceDate.getYear(), referenceDate.getMonthValue());
            return new Period(ym.atDay(1), ym.atEndOfMonth());
        }
        if (type == ReportType.SEMIANNUAL) {
            int year = referenceDate.getYear();
            int month = referenceDate.getMonthValue();
            int startMonth = month <= 6 ? 1 : 7;
            int endMonth = month <= 6 ? 6 : 12;
            LocalDate start = LocalDate.of(year, startMonth, 1);
            LocalDate end = YearMonth.of(year, endMonth).atEndOfMonth();
            return new Period(start, end);
        }
        LocalDate start = LocalDate.of(referenceDate.getYear(), 1, 1);
        LocalDate end = LocalDate.of(referenceDate.getYear(), 12, 31);
        return new Period(start, end);
    }

    private static Period resolvePreviousPeriod(ReportType type, Period current) {
        if (type == ReportType.MONTHLY) {
            YearMonth currentYm = YearMonth.from(current.start);
            YearMonth prev = currentYm.minusMonths(1);
            return new Period(prev.atDay(1), prev.atEndOfMonth());
        }
        if (type == ReportType.SEMIANNUAL) {
            LocalDate prevStart = current.start.minusMonths(6);
            LocalDate prevEnd = current.end.minusMonths(6);
            return new Period(prevStart, prevEnd);
        }
        LocalDate prevStart = current.start.minusYears(1);
        LocalDate prevEnd = current.end.minusYears(1);
        return new Period(prevStart, prevEnd);
    }

    private record Period(LocalDate start, LocalDate end) {}

    private record ComputedSummary(BigDecimal income, BigDecimal expenses, BigDecimal balance, BigDecimal savingsRate) {}
}
