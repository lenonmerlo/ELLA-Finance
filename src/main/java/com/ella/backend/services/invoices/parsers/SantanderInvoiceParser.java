package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class SantanderInvoiceParser implements InvoiceParserStrategy {

    // Ex.: Total a Pagar R$ 44.815,95 Vencimento 20/12/2025
    private static final Pattern HEADER_TOTAL_AND_DUE_PATTERN = Pattern.compile(
        "(?is)Total\\s+a\\s+Pagar\\s+R\\$\\s*([\\d.,]+)\\s+Vencimento\\s+(\\d{2}/\\d{2}(?:/\\d{4})?)"
    );
    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
        "(?is)\\b(vencimento|data\\s+de\\s+vencimento)\\b[^\\d]{0,30}(\\d{2}/\\d{2}(?:/\\d{4})?)"
    );
    private static final DateTimeFormatter DUE_DATE_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Ex.: ATILLA FERREGUETTI - 4258 XXXX XXXX 8854
    private static final Pattern HOLDER_BLOCK_PATTERN = Pattern.compile(
            "(?m)^([A-Z\\s]+)\\s+-\\s+(\\d{4})\\s+XXXX\\s+XXXX\\s+(\\d{4})\\s*$"
    );

    // Parcelamentos: dd/MM desc MM/TT amount
    private static final Pattern INSTALLMENT_LINE_PATTERN = Pattern.compile(
            "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(\\d{2}/\\d{2})\\s+(-?[\\d.,]+)\\s*$"
    );

    // Despesas comuns: dd/MM desc amount [usd]
    private static final Pattern EXPENSE_LINE_PATTERN = Pattern.compile(
            "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(-?[\\d.,]+)(?:\\s+([\\d.,]+))?\\s*$"
    );

    private enum Section {
        NONE,
        PAYMENTS_AND_CREDITS,
        INSTALLMENTS,
        EXPENSES
    }

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String normalizedText = normalizeNumericDates(text);
        String n = normalizeForSearch(text);
        boolean hasSantander = n.contains("santander");
        boolean hasDue = DUE_DATE_PATTERN.matcher(normalizedText).find() || HEADER_TOTAL_AND_DUE_PATTERN.matcher(normalizedText).find();
        boolean hasTotalToPay = n.contains("total a pagar");
        // Avoid false-positives: holder-like blocks can appear in other banks.
        return hasDue && (hasSantander || hasTotalToPay);
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        String normalizedText = normalizeNumericDates(text);

        List<Pattern> patterns = List.of(
                HEADER_TOTAL_AND_DUE_PATTERN,
                DUE_DATE_PATTERN
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalizedText);
            if (!m.find()) continue;

            // HEADER_TOTAL_AND_DUE_PATTERN has group(2), DUE_DATE_PATTERN has group(2)
            String value = m.group(2);
            LocalDate due = parseDueDate(value, normalizedText);
            if (due != null) return due;
        }
        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) return Collections.emptyList();

        List<TransactionData> out = new ArrayList<>();

        String currentCardName = null;
        String currentHolderName = null;
        Section section = Section.NONE;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            Matcher holder = HOLDER_BLOCK_PATTERN.matcher(line);
            if (holder.find()) {
                String holderName = safeTrim(holder.group(1));
                String first4 = safeTrim(holder.group(2));
                String last4 = safeTrim(holder.group(3));
                currentHolderName = holderName.isEmpty() ? null : holderName;
                currentCardName = "Santander"
                    + (last4.isEmpty() ? "" : " " + last4)
                    + (first4.isEmpty() ? "" : " (" + first4 + ")");
                section = Section.NONE;
                continue;
            }

            String nLine = normalizeForSearch(line);
            // Seções (heurísticas: funcionam com títulos comuns; PDFs reais podem variar)
            if (nLine.contains("pagamentos") || nLine.contains("debitos") || nLine.contains("creditos") || nLine.contains("créditos")) {
                section = Section.PAYMENTS_AND_CREDITS;
                // não dá continue: algumas faturas têm dados na mesma linha, mas é raro
            } else if (nLine.contains("parcel") || nLine.contains("parcelamentos") || nLine.contains("compras parceladas")) {
                section = Section.INSTALLMENTS;
            } else if (nLine.contains("despesas") || nLine.contains("compras") || nLine.contains("lancamentos") || nLine.contains("lançamentos")) {
                section = Section.EXPENSES;
            } else if (nLine.contains("resumo") && nLine.contains("fatura")) {
                section = Section.NONE;
            }

            // Parse das linhas de transação (sempre começam com dd/MM)
            if (!looksLikeTxLine(line)) continue;

            TransactionData td = parseTxLine(line, currentCardName, dueDate, section);
            if (td != null) {
                if (currentHolderName != null && !currentHolderName.isBlank()) {
                    td.cardholderName = currentHolderName;
                }
                out.add(td);
            }
        }

        return out;
    }

    private boolean looksLikeTxLine(String line) {
        if (line == null) return false;
        return line.matches("^\\d{2}/\\d{2}\\s+.*");
    }

    private TransactionData parseTxLine(String line, String cardName, LocalDate dueDate, Section section) {
        try {
            String cleaned = line.replace("R$", " ").replace("US$", " ").trim();
            cleaned = cleaned.replaceAll("\\s+", " ");

            LocalDate purchaseDate;
            String description;
            BigDecimal amount;
            Integer instNum = null;
            Integer instTot = null;

            // 1) Seção parcelamentos (mais específica)
            Matcher inst = INSTALLMENT_LINE_PATTERN.matcher(cleaned);
            if (inst.find()) {
                purchaseDate = parsePurchaseDate(inst.group(1), dueDate);
                description = safeTrim(inst.group(2));
                InstallmentInfo info = parseInstallment(inst.group(3));
                if (info != null) {
                    instNum = info.number();
                    instTot = info.total();
                }
                amount = parseNumberAmount(inst.group(4));
            } else {
                // 2) Linha comum: dd/MM desc amount [usd]
                Matcher exp = EXPENSE_LINE_PATTERN.matcher(cleaned);
                if (!exp.find()) return null;

                purchaseDate = parsePurchaseDate(exp.group(1), dueDate);
                description = safeTrim(exp.group(2));

                BigDecimal maybeBrl = parseNumberAmount(exp.group(3));
                BigDecimal maybeUsd = parseNumberAmount(exp.group(4));

                // Se houver US$, o BRL normalmente é o penúltimo (group 3)
                amount = (maybeBrl != null ? maybeBrl : maybeUsd);
            }

            if (purchaseDate == null || description.isEmpty() || amount == null) return null;

            TransactionType type = inferType(description, amount, section);
            String category = categorize(description, type, section);

            TransactionData td = new TransactionData(
                    description,
                    amount.abs(),
                    type,
                    category,
                    purchaseDate,
                    cardName,
                    TransactionScope.PERSONAL
            );
            if (instNum != null && instTot != null) {
                td.installmentNumber = instNum;
                td.installmentTotal = instTot;
            }
            return td;
        } catch (Exception ignored) {
            return null;
        }
    }

    private TransactionType inferType(String description, BigDecimal amount, Section section) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.INCOME;
        }
        String n = normalizeForSearch(description);

        // Dentro da seção de pagamentos/créditos é comum ter créditos/estornos
        if (section == Section.PAYMENTS_AND_CREDITS) {
            if (n.contains("pagamento") || n.contains("deb autom") || n.contains("debito autom") || n.contains("déb autom")
                    || n.contains("credito") || n.contains("crédito") || n.contains("estorno")) {
                return TransactionType.INCOME;
            }
        }

        if (n.contains("pagamento") || n.contains("deb autom") || n.contains("debito autom") || n.contains("déb autom")
                || n.contains("credito") || n.contains("crédito") || n.contains("estorno")) {
            return TransactionType.INCOME;
        }

        return TransactionType.EXPENSE;
    }

    private String categorize(String description, TransactionType type, Section section) {
        if (type == TransactionType.INCOME) {
            String n = normalizeForSearch(description);
            if (n.contains("pagamento") || n.contains("deb autom") || n.contains("debito autom") || n.contains("déb autom")
                    || n.contains("fatura")) {
                return "Pagamento";
            }
            if (n.contains("credito") || n.contains("crédito") || n.contains("estorno")) {
                return "Reembolso";
            }
        }

        // Santander: para algumas descrições, o guia espera Alimentação (ex.: RESTAURANTE, CAFE)
        // mesmo que o mapper global classifique como Lazer.
        if (type == TransactionType.EXPENSE) {
            String n = normalizeForSearch(description);
            if (n.contains("restaurante") || n.contains("cafe") || n.contains("café") || n.contains("padaria")
                    || n.contains("churrasc") || n.contains("pizzaria") || n.contains("lanchonete")) {
                return "Alimentação";
            }
        }

        // Parcelamentos são despesas; anuidade já cai em Taxas e Juros no mapper central
        return MerchantCategoryMapper.categorize(description, type);
    }

    private LocalDate parsePurchaseDate(String ddmm, LocalDate dueDate) {
        try {
            if (ddmm == null || ddmm.isBlank()) return null;
            String[] parts = ddmm.trim().split("/");
            if (parts.length < 2) return null;
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = dueDate != null ? dueDate.getYear() : LocalDate.now().getYear();
            if (dueDate != null && month > dueDate.getMonthValue()) {
                year = year - 1;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseNumberAmount(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;
            boolean negative = v.startsWith("-");
            v = v.replace("-", "");
            v = v.replace(".", "").replace(",", ".");
            BigDecimal bd = new BigDecimal(v);
            return negative ? bd.negate() : bd;
        } catch (Exception e) {
            return null;
        }
    }

    private InstallmentInfo parseInstallment(String mmtt) {
        try {
            if (mmtt == null || mmtt.isBlank()) return null;
            String[] parts = mmtt.trim().split("/");
            if (parts.length != 2) return null;
            int number = Integer.parseInt(parts[0]);
            int total = Integer.parseInt(parts[1]);
            if (number <= 0 || total <= 0) return null;
            return new InstallmentInfo(number, total);
        } catch (Exception e) {
            return null;
        }
    }

    private record InstallmentInfo(Integer number, Integer total) {
    }

    private LocalDate parseDueDate(String value, String fullText) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;

            // Defensive: tolerate spaces around separators if any slipped through.
            v = v.replaceAll("\\s+", "");

            // dd/MM/yyyy
            if (v.matches("^\\d{2}/\\d{2}/\\d{4}$")) {
                return LocalDate.parse(v, DUE_DATE_DDMMYYYY);
            }

            // dd/MM (infer year)
            if (v.matches("^\\d{2}/\\d{2}$")) {
                String[] parts = v.split("/");
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);

                Integer inferredYear = inferYearFromText(fullText);
                int year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                return LocalDate.of(year, month, day);
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Integer inferYearFromText(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = Pattern.compile("(?s)\\b\\d{2}/\\d{2}/(\\d{4})\\b").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String normalizeNumericDates(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\u00A0', ' ');
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        t = t.replaceAll("\\s*([\\./-])\\s*", "$1");
        return t;
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
}
