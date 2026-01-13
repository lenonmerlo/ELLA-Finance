package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class C6InvoiceParser implements InvoiceParserStrategy {

    private enum Section {
        NONE,
        TRANSACTIONS,
        SUMMARY,
        PAYMENT_OPTIONS
    }

    private final Set<String> seenTransactions = new HashSet<>();

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})");

    // Ex.: "C6 Carbon Virtual Final 5867 - LENON MERLO"
    // Ex.: "C6 Carbon Final: 5867" (sem titular)
    private static final Pattern CARD_HEADER_PATTERN = Pattern.compile(
            "(?i)^c6\\s+(.+?)\\s+final\\s*[:]?\\s*(\\d{4})(?:\\s*-\\s*(.+))?$");

    // Ex.: 27 out AIRBNB * HMF99EFWK9 - Parcela 2/3 369,48
    // Ex.: 14 nov BAR PIMENTA CARIOCA 92,40
    private static final Pattern TX_LINE_PATTERN = Pattern.compile(
            "(?i)^(\\d{1,2})\\s+([a-z0-9]{3})\\s+(.+?)(?:\\s+-\\s+parcela\\s+(\\d+)\\s*/\\s*(\\d+))?\\s+(-?[\\d\\.,]+)\\s*$");

    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);
        if (n.contains("c6 bank") || n.contains("c6bank")) return true;

        // Marcadores adicionais comuns no PDF.
        if (n.contains("c6") && (n.contains("cartao") || n.contains("cartão")) && n.contains("venc")) {
            return true;
        }

        // Alguns layouts do C6 vêm como "fatura" e podem não conter explicitamente "cartão" no texto extraído.
        if (n.contains("c6") && n.contains("fatura") && n.contains("venc")) {
            return true;
        }

        // Fallback: detecta pelo padrão de cabeçalho de cartão.
        Matcher m = CARD_HEADER_PATTERN.matcher(text);
        return m.find();
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = normalizeNumericDates(text);

        Integer inferredYear = inferYearFromText(normalized);

        List<Pattern> patterns = List.of(
                // dd/MM/yyyy
            Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b[^0-9]{0,40}(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})"),
                // dd/MM (sem ano)
            Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b[^0-9]{0,40}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?!\\s*[\\./-]\\s*\\d{4})"),
                // Textual: "Vencimento: 20 DEZ 2025" / "Data de vencimento 20 DEZ"
            Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b[^0-9]{0,60}(\\d{2})\\s+(?:de\\s+)?([A-Z]{3,9})\\s+(\\d{4})"),
            Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b[^0-9]{0,60}(\\d{2})\\s+(?:de\\s+)?([A-Z]{3,9})(?!\\s+\\d{4})"),
                // Ultra-flexível quando o PDF quebra dígitos: "Vencimento: 2 0 / 1 2 / 2 0 2 5"
            Pattern.compile("(?is)\\b(venc(?:imento)?|data\\s+de\\s+vencimento|data\\s+do\\s+vencimento)\\b[^0-9]{0,60}([0-9][0-9\\s\\./-]{5,30})"),
                // fallback compat (single group)
                DUE_DATE_PATTERN
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalized);
            if (!m.find()) continue;

            LocalDate due = extractDueFromMatcher(m, inferredYear);
            if (due != null) return due;
        }

        return null;
    }

    private LocalDate extractDueFromMatcher(Matcher m, Integer inferredYear) {
        try {
            if (m == null) return null;

            // numeric (keyword, dd, MM, yyyy)
            if (m.groupCount() >= 4 && isTwoDigits(m.group(2)) && isTwoDigits(m.group(3)) && isFourDigits(m.group(4))) {
                Integer day = parseIntOrNull(m.group(2));
                Integer month = parseIntOrNull(m.group(3));
                Integer year = parseIntOrNull(m.group(4));
                return safeDate(year, month, day);
            }

            // numeric without year (keyword, dd, MM)
            if (m.groupCount() >= 3 && isTwoDigits(m.group(2)) && isTwoDigits(m.group(3)) && !isFourDigitsSafe(m.group(4))) {
                Integer day = parseIntOrNull(m.group(2));
                Integer month = parseIntOrNull(m.group(3));
                Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                LocalDate d = safeDate(year, month, day);
                if (d == null) return null;

                // Se o documento não tem ano inferível, ajusta para o próximo ano quando fizer sentido.
                if (inferredYear == null) {
                    LocalDate now = LocalDate.now();
                    if (d.isBefore(now.minusDays(30))) {
                        d = safeDate(year + 1, month, day);
                    }
                }
                return d;
            }

            // textual with year (keyword, dd, MON, yyyy)
            if (m.groupCount() >= 4 && isTwoDigits(m.group(2)) && m.group(3) != null && isFourDigits(m.group(4))) {
                Integer day = parseIntOrNull(m.group(2));
                Integer month = monthAbbrevToNumber(m.group(3));
                Integer year = parseIntOrNull(m.group(4));
                return safeDate(year, month, day);
            }

            // textual without year (keyword, dd, MON)
            if (m.groupCount() >= 3 && isTwoDigits(m.group(2)) && m.group(3) != null && !isFourDigitsSafe(m.group(4))) {
                Integer day = parseIntOrNull(m.group(2));
                Integer month = monthAbbrevToNumber(m.group(3));
                Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                return safeDate(year, month, day);
            }

            // digits chunk (keyword, digits)
            if (m.groupCount() >= 2) {
                String digits = safeTrim(m.group(2)).replaceAll("\\D", "");
                if (digits.length() >= 8) {
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    Integer year = parseIntOrNull(digits.substring(4, 8));
                    return safeDate(year, month, day);
                }
                if (digits.length() >= 4) {
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                    return safeDate(year, month, day);
                }
            }

            // fallback: single group dd/MM/yyyy
            if (m.groupCount() >= 2 && m.group(2) != null && m.group(2).matches("\\d{2}/\\d{2}/\\d{4}")) {
                return parseDueDate(m.group(2));
            }
            if (m.groupCount() >= 1 && m.group(1) != null && m.group(1).matches("\\d{2}/\\d{2}/\\d{4}")) {
                return parseDueDate(m.group(1));
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTwoDigits(String s) {
        return s != null && s.matches("\\d{2}");
    }

    private boolean isFourDigits(String s) {
        return s != null && s.matches("\\d{4}");
    }

    private boolean isFourDigitsSafe(String s) {
        return s != null && s.matches("\\d{4}");
    }

    private Integer inferYearFromText(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            Matcher m = Pattern.compile("(?s)\\b\\d{2}/\\d{2}/(\\d{4})\\b").matcher(text);
            if (m.find()) {
                return parseIntOrNull(m.group(1));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer monthAbbrevToNumber(String mon) {
        if (mon == null) return null;
        int m = getMonthFromAbbreviation(mon);
        return m < 1 ? null : m;
    }

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        try {
            if (year == null || month == null || day == null) return null;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        System.out.println("[C6Parser] Starting extraction...");

        LocalDate dueDate = extractDueDate(text);
        System.out.println("[C6Parser] Due date: " + dueDate);
        if (dueDate == null) {
            // Mantém comportamento consistente com outros parsers: se não houver vencimento,
            // ainda tentamos extrair transações, mas a purchaseDate pode ficar incompleta.
        }

        List<TransactionData> out = new ArrayList<>();
        String currentCardName = null;

        // Garante que o deduplicador não vaze entre execuções (instância pode ser reutilizada).
        seenTransactions.clear();

        Section section = Section.NONE;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            // Alguns extratores preservam pipes/colunas; normaliza para facilitar o regex.
            line = line.replace("|", " ").replaceAll("\\s+", " ").trim();

            String nLine = normalizeForSearch(line);
            if (nLine.contains("resumo") && nLine.contains("fatura")) {
                section = Section.SUMMARY;
                continue;
            }
            if (nLine.contains("transacoes") || nLine.contains("transações")) {
                section = Section.TRANSACTIONS;
                continue;
            }

            if (section == Section.SUMMARY) {
                if (nLine.contains("compras") || nLine.contains("juros") || nLine.contains("tarifa") || nLine.contains("total a pagar")) {
                    continue;
                }
            }

            Matcher cardMatcher = CARD_HEADER_PATTERN.matcher(line);
            if (cardMatcher.find()) {
                String card = safeTrim(cardMatcher.group(1));
                String last4 = safeTrim(cardMatcher.group(2));
                String newCardName = (card.isEmpty() ? "C6" : card) + (last4.isEmpty() ? "" : " " + last4);

                if (currentCardName != null && !currentCardName.equals(newCardName)) {
                    seenTransactions.clear();
                }

                currentCardName = newCardName;
                System.out.println("[C6Parser] Found card: " + currentCardName);
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

            String lowerDesc = desc.toLowerCase();
            if (lowerDesc.contains("pagamento") || lowerDesc.contains("estorno") || lowerDesc.contains("credito") || lowerDesc.contains("crédito") || lowerDesc.contains("inclusao")) {
                type = TransactionType.INCOME;
            }

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

            if (!isValidTransaction(td)) {
                System.out.println("[C6Parser] INVALID TRANSACTION SKIPPED: " + desc);
                continue;
            }

            String dedupKey = createDedupKey(td);
            if (seenTransactions.contains(dedupKey)) {
                System.out.println("[C6Parser] DUPLICATE SKIPPED: " + desc + " = " + td.amount);
                continue;
            }
            seenTransactions.add(dedupKey);

            System.out.println("[C6Parser] [" + currentCardName + "] Extracted: " + desc + " = " + amount.abs() + " [" + td.date + "]");
            out.add(td);
        }

        System.out.println("[C6Parser] Total extracted: " + out.size() + " transactions");
        return out;
    }

    private String createDedupKey(TransactionData tx) {
        if (tx == null) return "";
        // Dedup: data + descrição (normalizada) + valor
        String d = tx.date == null ? "" : tx.date.toString();
        String desc = tx.description == null ? "" : normalizeForSearch(tx.description);
        String a = tx.amount == null ? "" : tx.amount.toPlainString();
        return d + "|" + desc + "|" + a;
    }

    private int parseMonth(String monthStr) {
        int month = getMonthFromAbbreviation(monthStr);
        return month < 1 ? 1 : month;
    }

    private int inferYearFromMonth(int month, LocalDate dueDate) {
        if (dueDate == null) return LocalDate.now().getYear();

        int dueMonth = dueDate.getMonthValue();
        int dueYear = dueDate.getYear();

        // Se mês da transação <= mês do vencimento, mesmo ano
        if (month <= dueMonth) {
            return dueYear;
        }

        // Se mês da transação > mês do vencimento, ano anterior
        return dueYear - 1;
    }

    private boolean isValidTransaction(TransactionData tx) {
        if (tx == null) return false;

        if (tx.description == null || tx.description.length() < 2) return false;
        if (tx.amount == null || tx.amount.signum() == 0) return false;
        if (tx.date == null) return false;
        if (tx.type == null) return false;

        return true;
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
        String cleaned = mon.trim().toLowerCase();
        // Alguns PDFs vêm com OCR/extração trocando 'o' por '0' (ex.: "n0v", "0ut").
        cleaned = cleaned.replace('0', 'o');

        return switch (cleaned) {
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
            // mês por extenso (PT-BR)
            case "janeiro" -> 1;
            case "fevereiro" -> 2;
            case "marco", "março" -> 3;
            case "abril" -> 4;
            case "maio" -> 5;
            case "junho" -> 6;
            case "julho" -> 7;
            case "agosto" -> 8;
            case "setembro" -> 9;
            case "outubro" -> 10;
            case "novembro" -> 11;
            case "dezembro" -> 12;
            default -> -1;
        };
    }

    private LocalDate buildPurchaseDate(int day, String mon, LocalDate dueDate) {
        try {
            int month = parseMonth(mon);
            int year = inferYearFromMonth(month, dueDate);

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
