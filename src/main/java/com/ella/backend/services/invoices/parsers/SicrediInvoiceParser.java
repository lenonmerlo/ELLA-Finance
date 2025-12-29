package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class SicrediInvoiceParser implements InvoiceParserStrategy {

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile("(?is)\\bvencimento\\b\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Linha de transação normalmente inicia com "dd/mon" (ex.: 11/nov 06:13)
    private static final Pattern TX_LINE_START_PATTERN = Pattern.compile("(?i)^\\d{2}/[a-z]{3}\\b");
    private static final Pattern DATE_TIME_PATTERN = Pattern.compile("(?i)^(\\d{2})/([a-z]{3})(?:\\s+\\d{2}:\\d{2})?.*$");
    private static final Pattern INSTALLMENT_PATTERN = Pattern.compile("^(\\d{2})/(\\d{2})$");

    private static final Map<String, Integer> MONTHS = buildMonths();

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);
        boolean hasSicrediMarker = n.contains("sicredi");
        boolean hasDue = DUE_DATE_PATTERN.matcher(normalizeNumericDates(text)).find();
        boolean hasTableMarkers = n.contains("data e hora") && (n.contains("valor em reais") || n.contains("valor em reais"));
        return (hasSicrediMarker && hasDue) || (hasDue && hasTableMarkers);
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        String normalizedText = normalizeNumericDates(text);

        List<Pattern> patterns = List.of(
                // dd/MM/yyyy
                Pattern.compile("(?is)\\bvencimento\\b\\s*[:\\-]?\\s*(\\d{2})\\s*/\\s*(\\d{2})\\s*/\\s*(\\d{4})"),
                // legacy single-group
                DUE_DATE_PATTERN
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalizedText);
            if (!m.find()) continue;

            String value;
            if (m.groupCount() >= 3) {
                value = m.group(1) + "/" + m.group(2) + "/" + m.group(3);
            } else {
                value = m.group(1);
            }

            try {
                return LocalDate.parse(value.trim().replaceAll("\\s+", ""), DUE_DATE_FORMATTER);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) return Collections.emptyList();

        List<TransactionData> out = new ArrayList<>();

        boolean inTransactionSection = false;
        String currentCard = null;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            String nLine = normalizeForSearch(line);
            if (nLine.startsWith("cartao ") || nLine.startsWith("cartão ")) {
                currentCard = line;
                continue;
            }

            if (nLine.contains("data e hora") && (nLine.contains("valor em reais") || nLine.contains("valor em reais"))) {
                inTransactionSection = true;
                continue;
            }

            if (!inTransactionSection) continue;

            if (!TX_LINE_START_PATTERN.matcher(line).find()) {
                continue;
            }

            TransactionData tx = parseTxLine(line, currentCard, dueDate);
            if (tx != null) out.add(tx);
        }

        return out;
    }

    private TransactionData parseTxLine(String line, String currentCard, LocalDate dueDate) {
        try {
            // A fatura é tabular: divide por 2+ espaços, preservando campos com 1 espaço.
            String[] parts = line.split("\\s{2,}");
            if (parts.length < 4) return null;

            String dateTimeStr = safeTrim(parts[0]);
            LocalDate purchaseDate = parsePurchaseDate(dateTimeStr, dueDate);
            if (purchaseDate == null) return null;

            // Colunas típicas: 0=data/hora, 1=cidade, 2=Online/Presencial, 3=descrição, ... , last=valor em reais
            String description = safeTrim(parts[3]);
            if (description.isEmpty()) return null;

            String valueStr = safeTrim(parts[parts.length - 1]);
            BigDecimal amount = parseBrlAmount(valueStr);
            if (amount == null) return null;

            InstallmentInfo installment = findInstallment(parts);

            TransactionType type = inferType(description, amount);

            String category;
            if (type == TransactionType.EXPENSE) {
                category = categorizeForSicredi(description);
            } else {
                category = MerchantCategoryMapper.categorize(description, type);
            }

            TransactionData td = new TransactionData(
                    description,
                    amount.abs(),
                    type,
                    category,
                    purchaseDate,
                    currentCard,
                    TransactionScope.PERSONAL
            );
            if (installment != null) {
                td.installmentNumber = installment.number();
                td.installmentTotal = installment.total();
            }
            return td;
        } catch (Exception ignored) {
            return null;
        }
    }

    private TransactionType inferType(String description, BigDecimal amount) {
        if (amount == null) return TransactionType.EXPENSE;
        if (amount.compareTo(BigDecimal.ZERO) < 0) return TransactionType.INCOME;

        String n = normalizeForSearch(description);
        if (n.contains("pagamento") || n.contains("credito") || n.contains("crédito") || n.contains("estorno")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String categorizeForSicredi(String description) {
        if (description == null) return "Outros";
        String n = normalizeForSearch(description);

        // IOF / taxas bancárias
        if (n.contains("iof") || n.contains("anuidade")) {
            return "Taxas e Juros";
        }

        // Delegamos para o mapper central para manter consistência geral
        return MerchantCategoryMapper.categorize(description, TransactionType.EXPENSE);
    }

    private InstallmentInfo findInstallment(String[] parts) {
        if (parts == null) return null;
        for (int i = 0; i < parts.length; i++) {
            String p = safeTrim(parts[i]);
            if (p.isEmpty()) continue;
            Matcher m = INSTALLMENT_PATTERN.matcher(p);
            if (!m.find()) continue;
            Integer num = parseIntOrNull(m.group(1));
            Integer tot = parseIntOrNull(m.group(2));
            if (num != null && tot != null && num > 0 && tot > 0) {
                return new InstallmentInfo(num, tot);
            }
        }
        return null;
    }

    private record InstallmentInfo(Integer number, Integer total) {}

    private LocalDate parsePurchaseDate(String dateTimeStr, LocalDate dueDate) {
        if (dateTimeStr == null || dateTimeStr.isBlank()) return null;
        Matcher m = DATE_TIME_PATTERN.matcher(dateTimeStr.trim());
        if (!m.find()) return null;

        Integer day = parseIntOrNull(m.group(1));
        String mon = safeTrim(m.group(2)).toLowerCase(Locale.ROOT);
        if (day == null || mon.isEmpty()) return null;

        Integer month = MONTHS.get(mon);
        if (month == null) return null;

        int year = dueDate != null ? dueDate.getYear() : LocalDate.now().getYear();
        if (dueDate != null && month > dueDate.getMonthValue()) {
            year = year - 1;
        }

        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBrlAmount(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;

            v = v.replace("R$", "").replace(" ", "");
            boolean negative = v.startsWith("-") || v.contains("-" + "");
            v = v.replace("-", "");
            v = v.replace(".", "").replace(",", ".");
            BigDecimal bd = new BigDecimal(v);
            return negative ? bd.negate() : bd;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseIntOrNull(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeNumericDates(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\u00A0', ' ');
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        t = t.replaceAll("\\s*([\\./-])\\s*", "$1");
        return t;
    }

    private static Map<String, Integer> buildMonths() {
        Map<String, Integer> m = new HashMap<>();
        m.put("jan", 1);
        m.put("fev", 2);
        m.put("mar", 3);
        m.put("abr", 4);
        m.put("mai", 5);
        m.put("jun", 6);
        m.put("jul", 7);
        m.put("ago", 8);
        m.put("set", 9);
        m.put("out", 10);
        m.put("nov", 11);
        m.put("dez", 12);
        return m;
    }
}
