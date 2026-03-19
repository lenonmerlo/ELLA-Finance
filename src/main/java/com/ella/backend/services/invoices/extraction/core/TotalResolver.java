package com.ella.backend.services.invoices.extraction.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TotalResolver {

    private TotalResolver() {
    }

    public static BigDecimal extractInvoiceExpectedTotal(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = text.replace('\u00A0', ' ');
        String[] lines = normalized.split("\\r?\\n");

        Pattern brlMoneyStrict = Pattern.compile("(?i)(?:R\\$\\s*)?((?:[0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]+),[0-9]{2})");

        List<BigDecimal> highPriorityCandidates = new ArrayList<>();
        List<BigDecimal> mediumPriorityCandidates = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;

            String nLine = line.toLowerCase(Locale.ROOT);

            boolean isHighPriorityLabel = nLine.contains("valor da fatura")
                    || nLine.contains("total da fatura")
                    || nLine.contains("total fatura")
                    || nLine.contains("total desta fatura")
                    || nLine.contains("chegou no valor")
                    || nLine.contains("lancamentos atuais")
                    || nLine.contains("lançamentos atuais")
                    || nLine.contains("total dos lancamentos atuais")
                    || nLine.contains("total dos lançamentos atuais")
                    || nLine.matches(".*\\(\\+\\)\\s*compras\\s*/\\s*d[eé]bitos.*");

            boolean isTotalAPagar = nLine.contains("total a pagar");

            if (!isHighPriorityLabel && !isTotalAPagar) {
                continue;
            }

            if (nLine.contains("fatura anterior")) {
                continue;
            }

            boolean parcelamentoContext = hasParcelamentoContext(lines, i);

            BigDecimal contextualValue = extractFirstMoneyFromWindow(lines, i, brlMoneyStrict);
            if (contextualValue == null) continue;

            if (isHighPriorityLabel) {
                highPriorityCandidates.add(contextualValue);
            } else if (isTotalAPagar && !parcelamentoContext) {
                mediumPriorityCandidates.add(contextualValue);
            }
        }

        BigDecimal high = pickMostFrequent(highPriorityCandidates);
        if (high != null) return high;

        BigDecimal medium = pickMostFrequent(mediumPriorityCandidates);
        if (medium != null) return medium;

        return null;
    }

    private static boolean hasParcelamentoContext(String[] lines, int index) {
        if (lines == null || lines.length == 0) return false;
        int from = Math.max(0, index - 1);
        int to = Math.min(lines.length - 1, index + 2);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            if (lines[i] == null) continue;
            sb.append(lines[i]).append(' ');
        }
        String ctx = sb.toString().toLowerCase(Locale.ROOT);
        return ctx.contains("parcelamento") || ctx.contains("simula") || ctx.contains("entrada +");
    }

    private static BigDecimal extractFirstMoneyFromWindow(String[] lines, int index, Pattern moneyPattern) {
        int from = Math.max(0, index);
        int to = Math.min(lines.length - 1, index + 4);

        for (int i = from; i <= to; i++) {
            String line = lines[i] == null ? "" : lines[i];
            Matcher matcher = moneyPattern.matcher(line);
            while (matcher.find()) {
                BigDecimal v = parseBrlAmountLoose(matcher.group(1));
                if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                    return v;
                }
            }
        }

        return null;
    }

    private static BigDecimal pickMostFrequent(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;

        Map<BigDecimal, Integer> frequency = new HashMap<>();
        for (BigDecimal v : values) {
            if (v == null) continue;
            frequency.merge(v, 1, Integer::sum);
        }

        BigDecimal chosen = null;
        int maxCount = -1;
        for (Map.Entry<BigDecimal, Integer> entry : frequency.entrySet()) {
            BigDecimal value = entry.getKey();
            int count = entry.getValue();
            if (count > maxCount) {
                maxCount = count;
                chosen = value;
                continue;
            }
            if (count == maxCount && chosen != null && value.compareTo(chosen) > 0) {
                chosen = value;
            }
        }

        return chosen;
    }

    private static BigDecimal parseBrlAmountLoose(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        s = s.replaceAll("[^0-9,\\.]", "");
        if (s.isEmpty()) return null;

        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            if (!s.matches("-?\\d+\\.\\d{2}")) {
                s = s.replace(".", "");
            }
        }

        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}
