package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class BancoDoBrasilInvoiceParser implements InvoiceParserStrategy {

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
            "(?is)\\bVencimento\\b\\s+(\\d{2}/\\d{2}/\\d{4})"
    );
    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Ex.: "Titular: MARIANA OLIVEIRA" (variações comuns em PDFs)
        private static final Pattern HOLDER_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:nome\\s+do\\s+titular|titular|cliente|nome)\\s*[:\\-]?\\s*(.+?)\\s*$"
        );

    // Ex.: 20/08 | PGTO. COBRANCA ... | BR | R$ -84,00
    // Ex.: 21/08 | WWW.STATUE... | TX | R$ 79,68
    private static final Pattern TX_LINE_PATTERN = Pattern.compile(
            "^(\\d{2}/\\d{2})\\s+(.+?)\\s+([A-Z]{2})?\\s+R\\$\\s*([-\\d.,]+)\\s*$"
    );

    // Ex.:       | *** 14,00 DOLAR AMERICANO
    private static final Pattern DOLLAR_LINE_PATTERN = Pattern.compile(
            "^\\s*\\*\\*\\*\\s+([\\d,.]+)\\s+DOLAR\\s+AMERICANO\\b.*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> CATEGORY_HEADERS = Set.of(
            "LAZER",
            "RESTAURANTES",
            "SERVICOS",
            "SERVIÇOS",
            "VESTUARIO",
            "VESTUÁRIO",
            "VIAGENS",
            "OUTROS LANCAMENTOS",
            "OUTROS LANÇAMENTOS"
    );

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);
        boolean hasBrand = n.contains("banco do brasil") || n.contains("ourocard") || n.contains("bb");
        boolean hasDue = DUE_DATE_PATTERN.matcher(text).find();
        // Evita falso positivo por "BB" genérico: exige vencimento
        return hasBrand && hasDue;
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = DUE_DATE_PATTERN.matcher(text);
        if (!m.find()) return null;
        try {
            return LocalDate.parse(m.group(1), DUE_DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) return Collections.emptyList();

        String holderName = extractHolderName(text);

        List<TransactionData> out = new ArrayList<>();

        boolean inTransactions = false;
        boolean skippingIntlContinuation = false;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            if (raw == null) continue;
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;

            String nLine = normalizeForSearch(trimmed);

            if (!inTransactions) {
                // Heurística: cabeçalho da tabela ("Descrição País Valor")
                if (nLine.contains("descricao") && nLine.contains("valor") && nLine.contains("pais")) {
                    inTransactions = true;
                }
                continue;
            }

            // Fim típico da seção
            if (nLine.contains("total da fatura")
                    || (nLine.contains("resumo") && nLine.contains("fatura"))) {
                break;
            }

            // Ignorar headers de categoria do BB (não são transação)
            if (isCategoryHeader(trimmed)) {
                continue;
            }

            // Continuação de compra internacional (linhas indentadas de dólar/cotação)
            if (skippingIntlContinuation) {
                boolean isIndented = raw.length() > 0 && Character.isWhitespace(raw.charAt(0));
                if (isIndented) {
                    if (DOLLAR_LINE_PATTERN.matcher(trimmed).matches()) {
                        continue;
                    }
                    if (nLine.contains("cotacao") && nLine.contains("dolar")) {
                        continue;
                    }
                    // outras linhas indentadas relacionadas (mantém conservador)
                    continue;
                }
                skippingIntlContinuation = false;
            }

            Matcher tx = TX_LINE_PATTERN.matcher(trimmed);
            if (!tx.find()) {
                continue;
            }

            String ddmm = safeTrim(tx.group(1));
            String description = safeTrim(tx.group(2));
            String country = safeTrim(tx.group(3));
            String amountStr = safeTrim(tx.group(4));

            if (description.isEmpty()) continue;

            LocalDate purchaseDate = parsePurchaseDate(ddmm, dueDate);
            BigDecimal signedAmount = parseAmount(amountStr);
            if (purchaseDate == null || signedAmount == null) continue;

            TransactionType type = inferType(description, signedAmount);
            String category = categorize(description, type);

            TransactionData td = new TransactionData(
                    description,
                    signedAmount.abs(),
                    type,
                    category,
                    purchaseDate,
                    "Banco do Brasil",
                    TransactionScope.PERSONAL
            );
            if (holderName != null && !holderName.isBlank()) {
                td.cardholderName = holderName;
            }
            out.add(td);

            if (!country.isEmpty() && !"BR".equalsIgnoreCase(country)) {
                skippingIntlContinuation = true;
            }
        }

        return out;
    }

    private String extractHolderName(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = HOLDER_PATTERN.matcher(text);
        while (m.find()) {
            String v = safeTrim(m.group(1));
            if (v.isEmpty()) continue;
            // Evita capturar linhas genéricas do banco
            String n = normalizeForSearch(v);
            if (n.equals("banco do brasil") || n.equals("ourocard") || n.equals("bb")) {
                continue;
            }
            return v;
        }
        return null;
    }

    private boolean isCategoryHeader(String line) {
        if (line == null) return false;
        String n = normalize(line);
        return CATEGORY_HEADERS.contains(n) || CATEGORY_HEADERS.contains(n.replace("Ç", "C"));
    }

    private TransactionType inferType(String description, BigDecimal signedAmount) {
        if (signedAmount != null && signedAmount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.INCOME;
        }
        String n = normalizeForSearch(description);
        if (n.contains("pgto") || n.contains("pagamento") || n.contains("credito") || n.contains("crédito")
                || n.contains("estorno") || n.contains("reembolso")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private String categorize(String description, TransactionType type) {
        if (type == TransactionType.INCOME) {
            String n = normalizeForSearch(description);
            if (n.contains("pgto") || n.contains("pagamento")) return "Pagamento";
            if (n.contains("estorno") || n.contains("credito") || n.contains("crédito") || n.contains("reembolso")) {
                return "Reembolso";
            }
        }
        return MerchantCategoryMapper.categorize(description, type);
    }

    private LocalDate parsePurchaseDate(String ddmm, LocalDate dueDate) {
        try {
            if (ddmm == null || ddmm.isBlank() || dueDate == null) return null;
            String[] parts = ddmm.trim().split("/");
            if (parts.length < 2) return null;
            int day = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int year = dueDate.getYear();
            // Ex.: transação 10/12 com vencimento 05/01 => ano anterior
            if (month > dueDate.getMonthValue()) {
                year = year - 1;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseAmount(String value) {
        try {
            if (value == null || value.isBlank()) return null;
            String v = value.trim();
            // Mantém só dígitos, ponto, vírgula e sinal
            v = v.replaceAll("[^0-9,.-]", "");
            // remove separador de milhar e troca decimal
            v = v.replace(".", "").replace(",", ".");
            if (v.isBlank() || "-".equals(v) || ".".equals(v)) return null;
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toUpperCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String normalizeForSearch(String s) {
        return normalize(s).toLowerCase(Locale.ROOT);
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}
