package com.ella.backend.services.insights.providers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ella.backend.dto.dashboard.InsightDTO;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Person;
import com.ella.backend.services.insights.InsightDataCache;
import com.ella.backend.services.insights.InsightUtils;

@Component
@Order(40)
public class RecurringPaymentInsightProvider implements InsightProvider {

    private static final Locale LOCALE_PT_BR = Locale.of("pt", "BR");

    private static final Pattern MULTISPACE = Pattern.compile("\\s+");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final InsightDataCache insightDataCache;

    public RecurringPaymentInsightProvider(InsightDataCache insightDataCache) {
        this.insightDataCache = insightDataCache;
    }

    @Override
    public List<InsightDTO> generate(Person person, int year, int month) {
        YearMonth current = YearMonth.of(year, month);
        List<YearMonth> months = InsightDataCache.trailingMonthsInclusive(current, 3);
        if (months.size() < 3) {
            return List.of();
        }

        // Carrega tx dos 3 meses (inclui o mês atual)
        List<FinancialTransaction> all = new ArrayList<>();
        for (YearMonth ym : months) {
            all.addAll(insightDataCache.getCashflowTransactionsForMonth(person, ym));
        }

        if (all.isEmpty()) {
            return List.of();
        }

        // Filtra candidatos a assinatura
        List<FinancialTransaction> expenses = all.stream()
                .filter(Objects::nonNull)
                .filter(InsightUtils::isExpense)
                .filter(this::isLikelySubscriptionCandidate)
                .toList();

        if (expenses.isEmpty()) {
            return List.of();
        }

        Map<String, List<FinancialTransaction>> byDescriptionKey = expenses.stream()
                .collect(Collectors.groupingBy(t -> normalizeDescriptionKey(t.getDescription())));

        List<RecurringGroup> recurringGroups = new ArrayList<>();

        for (Map.Entry<String, List<FinancialTransaction>> entry : byDescriptionKey.entrySet()) {
            List<FinancialTransaction> groupTx = entry.getValue();
            if (groupTx == null || groupTx.size() < 3) {
                continue;
            }

            // Dentro da mesma descrição, clusteriza por valor aproximado
            List<AmountCluster> clusters = clusterByAmount(groupTx);
            for (AmountCluster cluster : clusters) {
                Optional<RecurringGroup> recurring = toRecurringIfMatchesThreeMonths(cluster, months);
                recurring.ifPresent(recurringGroups::add);
            }
        }

        if (recurringGroups.isEmpty()) {
            return List.of();
        }

        // Ordena por custo mensal estimado (desc)
        recurringGroups.sort(Comparator.comparing(RecurringGroup::estimatedMonthlyCost).reversed());

        int maxItemsToList = 4;
        List<RecurringGroup> top = recurringGroups.stream().limit(maxItemsToList).toList();

        BigDecimal total = recurringGroups.stream()
                .map(RecurringGroup::estimatedMonthlyCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String itemsText = top.stream()
                .map(g -> String.format("%s (%s)", g.displayName(), InsightUtils.formatCurrency(g.estimatedMonthlyCost())))
                .collect(Collectors.joining(", "));

        int remaining = recurringGroups.size() - top.size();
        if (remaining > 0) {
            itemsText = itemsText + String.format(" e mais %d", remaining);
        }

        return List.of(InsightDTO.builder()
                .type("info")
                .category("Assinaturas")
                .message(String.format(
                        "Você tem %d pagamentos recorrentes nos últimos 3 meses: %s. Total estimado: %s/mês.",
                        recurringGroups.size(),
                        itemsText,
                        InsightUtils.formatCurrency(total)
                ))
                .build());
    }

    private boolean isLikelySubscriptionCandidate(FinancialTransaction tx) {
        BigDecimal amount = InsightUtils.safeAmount(tx);
        if (amount.compareTo(BigDecimal.valueOf(5)) < 0) {
            return false;
        }
        // Evita alguns padrões comuns de transferências e afins
        String d = tx.getDescription() != null ? tx.getDescription().toLowerCase(LOCALE_PT_BR) : "";
        if (d.isBlank()) {
            return false;
        }

        String[] negativeHints = new String[]{
                "pix", "transfer", "ted", "doc", "boleto", "saque", "estorno", "ajuste", "fatura", "pagamento"
        };
        for (String hint : negativeHints) {
            if (d.contains(hint)) {
                return false;
            }
        }

        return true;
    }

    private String normalizeDescriptionKey(String description) {
        if (description == null) {
            return "";
        }
        String s = description.toLowerCase(LOCALE_PT_BR).trim();
        s = DIGITS.matcher(s).replaceAll("");
        s = s.replaceAll("[^a-zà-ú0-9 ]", " ");
        s = MULTISPACE.matcher(s).replaceAll(" ").trim();

        // Remove tokens muito comuns que só geram ruído em descrições (ex: "netflix.com")
        if (!s.isBlank()) {
            Set<String> stop = Set.of("com", "br", "www");
            s = List.of(s.split(" ")).stream()
                    .filter(t -> !t.isBlank())
                    .filter(t -> !stop.contains(t))
                    .collect(Collectors.joining(" "))
                    .trim();
        }
        return s;
    }

    private List<AmountCluster> clusterByAmount(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return List.of();
        }

        List<FinancialTransaction> sorted = txs.stream()
                .sorted(Comparator.comparing(InsightUtils::safeAmount))
                .toList();

        List<AmountCluster> clusters = new ArrayList<>();
        AmountCluster current = null;

        for (FinancialTransaction tx : sorted) {
            BigDecimal amount = InsightUtils.safeAmount(tx);
            if (current == null) {
                current = new AmountCluster();
                current.add(tx);
                continue;
            }

            BigDecimal reference = current.referenceAmount();
            BigDecimal tolerance = toleranceFor(reference);

            if (amount.subtract(reference).abs().compareTo(tolerance) <= 0) {
                current.add(tx);
            } else {
                clusters.add(current);
                current = new AmountCluster();
                current.add(tx);
            }
        }

        if (current != null) {
            clusters.add(current);
        }

        // remove clusters muito pequenas
        return clusters.stream().filter(c -> c.txs.size() >= 3).toList();
    }

    private BigDecimal toleranceFor(BigDecimal reference) {
        // 2 reais ou 5% (o que for maior)
        BigDecimal abs = BigDecimal.valueOf(2);
        BigDecimal pct = reference.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP);
        return abs.max(pct);
    }

    private Optional<RecurringGroup> toRecurringIfMatchesThreeMonths(AmountCluster cluster, List<YearMonth> months) {
        // Precisamos cobrir os 3 meses (inclusive current)
        Set<YearMonth> required = Set.copyOf(months);

        Map<YearMonth, List<FinancialTransaction>> byMonth = cluster.txs.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate())));

        if (!byMonth.keySet().containsAll(required)) {
            return Optional.empty();
        }

        // Regra extra: baixa variação de valor
        List<BigDecimal> amounts = cluster.txs.stream().map(InsightUtils::safeAmount).toList();
        double mean = InsightUtils.mean(amounts);
        double std = InsightUtils.stdDevSample(amounts, mean);

        if (mean <= 0.0) {
            return Optional.empty();
        }

        // std <= 8% da média ou <= 5 reais
        if (std > Math.max(5.0, mean * 0.08)) {
            return Optional.empty();
        }

        // Regra extra: dia do mês "parecido" (evita falsos positivos)
        int minDay = Integer.MAX_VALUE;
        int maxDay = Integer.MIN_VALUE;
        for (YearMonth ym : required) {
            FinancialTransaction tx = byMonth.get(ym).getFirst();
            int day = tx.getTransactionDate().getDayOfMonth();
            minDay = Math.min(minDay, day);
            maxDay = Math.max(maxDay, day);
        }
        if (maxDay - minDay > 10) {
            return Optional.empty();
        }

        // Nome de exibição: pega a descrição mais frequente
        String displayName = mostCommonDescription(cluster.txs);

        BigDecimal estimatedMonthly = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new RecurringGroup(displayName, estimatedMonthly));
    }

    private String mostCommonDescription(List<FinancialTransaction> txs) {
        if (txs == null || txs.isEmpty()) {
            return "Assinatura";
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FinancialTransaction tx : txs) {
            String d = tx.getDescription() != null ? tx.getDescription().trim() : "";
            if (d.isBlank()) {
                continue;
            }
            counts.put(d, counts.getOrDefault(d, 0L) + 1);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Assinatura");
    }

    private static final class AmountCluster {
        private final List<FinancialTransaction> txs = new ArrayList<>();

        void add(FinancialTransaction tx) {
            txs.add(tx);
        }

        BigDecimal referenceAmount() {
            if (txs.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return InsightUtils.safeAmount(txs.getFirst());
        }
    }

    private record RecurringGroup(String displayName, BigDecimal estimatedMonthlyCost) {
    }
}
