package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class C6InvoiceParser implements InvoiceParserStrategy {

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile("(?is)\\bvencimento\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern CARD_HEADER_PATTERN = Pattern.compile(
            "(?i)^c6\\s+(.+?)\\s+final\\s+(\\d{4})\\s*-\\s*(.+)$");

    // Ex.: 27 out AIRBNB * HMF99EFWK9 - Parcela 2/3 369,48
    // Ex.: 14 nov BAR PIMENTA CARIOCA 92,40
    private static final Pattern TX_LINE_PATTERN = Pattern.compile(
            "(?i)^(\\d{1,2})\\s+([a-z]{3})\\s+(.+?)(?:\\s+-\\s+parcela\\s+(\\d+)\\s*/\\s*(\\d+))?\\s+(-?[\\d\\.,]+)\\s*$");

    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);
        if (n.contains("c6 bank")) return true;

        // Fallback: detecta pelo padrão de cabeçalho de cartão.
        Matcher m = CARD_HEADER_PATTERN.matcher(text);
        return m.find();
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = normalizeNumericDates(text);

        List<Pattern> patterns = List.of(
                // dd/MM/yyyy
                Pattern.compile("(?is)\\bvencimento\\b\\s*[:\\-]?\\s*(\\d{2})\\s*/\\s*(\\d{2})\\s*/\\s*(\\d{4})"),
                // fallback compat (single group)
                DUE_DATE_PATTERN
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalized);
            if (!m.find()) continue;

            String value;
            if (m.groupCount() >= 3) {
                value = m.group(1) + "/" + m.group(2) + "/" + m.group(3);
            } else {
                value = m.group(1);
            }
            LocalDate due = parseDueDate(value);
            if (due != null) return due;
        }

        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) {
            // Mantém comportamento consistente com outros parsers: se não houver vencimento,
            // ainda tentamos extrair transações, mas a purchaseDate pode ficar incompleta.
        }

        List<TransactionData> out = new ArrayList<>();
        String currentCardName = null;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            // Alguns extratores preservam pipes/colunas; normaliza para facilitar o regex.
            line = line.replace("|", " ").replaceAll("\\s+", " ").trim();

            Matcher cardMatcher = CARD_HEADER_PATTERN.matcher(line);
            if (cardMatcher.find()) {
                String card = safeTrim(cardMatcher.group(1));
                String last4 = safeTrim(cardMatcher.group(2));
                currentCardName = (card.isEmpty() ? "C6" : card) + (last4.isEmpty() ? "" : " " + last4);
                continue;
            }

            Matcher txMatcher = TX_LINE_PATTERN.matcher(line);
            if (!txMatcher.find()) continue;

            Integer day = parseIntOrNull(txMatcher.group(1));
            String mon = safeTrim(txMatcher.group(2));
            String desc = safeTrim(txMatcher.group(3));
            Integer instNum = parseIntOrNull(txMatcher.group(4));
            Integer instTot = parseIntOrNull(txMatcher.group(5));
            BigDecimal amount = parseBrlAmount(txMatcher.group(6));

            if (day == null || mon.isEmpty() || desc.isEmpty() || amount == null) continue;

            LocalDate purchaseDate = buildPurchaseDate(day, mon, dueDate);
            TransactionType type = inferType(desc, amount);
                String category = MerchantCategoryMapper.categorize(desc, type);

            TransactionData td = new TransactionData(
                    desc,
                    amount.abs(),
                    type,
                    category,
                    purchaseDate,
                    currentCardName,
                    TransactionScope.PERSONAL
            );
            if (instNum != null && instTot != null && instNum > 0 && instTot > 0) {
                td.installmentNumber = instNum;
                td.installmentTotal = instTot;
            }
            out.add(td);
        }

        return out;
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private LocalDate parseDueDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DUE_DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeNumericDates(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\u00A0', ' ');
        // Remove spaces between digits that PDFBox may insert ("2 2/1 2/2025").
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        // Normalize spaces around separators.
        t = t.replaceAll("\\s*([\\./-])\\s*", "$1");
        return t;
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

    private int getMonthFromAbbreviation(String mon) {
        if (mon == null) return -1;
        return switch (mon.trim().toLowerCase()) {
            case "jan" -> 1;
            case "fev", "feb" -> 2;
            case "mar" -> 3;
            case "abr", "apr" -> 4;
            case "mai", "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "ago", "aug" -> 8;
            case "set", "sep" -> 9;
            case "out", "oct" -> 10;
            case "nov" -> 11;
            case "dez", "dec" -> 12;
            default -> -1;
        };
    }

    private LocalDate buildPurchaseDate(int day, String mon, LocalDate dueDate) {
        try {
            int month = getMonthFromAbbreviation(mon);
            if (month < 1) return null;

            int year = (dueDate != null ? dueDate.getYear() : LocalDate.now().getYear());
            if (dueDate != null && dueDate.getMonthValue() == 1 && month == 12) {
                year = year - 1;
            }

            LocalDate base = LocalDate.of(year, month, 1);
            int dom = Math.min(day, base.lengthOfMonth());
            return base.withDayOfMonth(dom);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBrlAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        // remove espaços e símbolos não numéricos comuns
        s = s.replace("R$", "").replace(" ", "").trim();

        // Se tiver vírgula, assume padrão BR (ponto milhar, vírgula decimal)
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            // Sem vírgula: se for x.yy usa ponto decimal; senão remove pontos (milhar)
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

    private TransactionType inferType(String description, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.INCOME;
        }

        String n = normalizeForSearch(description);
        if (n.contains("estorno") || n.contains("credito") || n.contains("pagamento") || n.contains("inclusao de pagamento")) {
            return TransactionType.INCOME;
        }

        return TransactionType.EXPENSE;
    }
}
