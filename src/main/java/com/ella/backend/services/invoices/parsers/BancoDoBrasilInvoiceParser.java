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

    private static final List<Pattern> DUE_DATE_PATTERNS = List.of(
        // Ex.: "Vencimento 20/12/2025", "VENCIMENTO: 20.12.2025", "Data de vencimento - 20-12-2025"
        Pattern.compile("(?is)\\b(?:venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})"),
        // Ex.: "Vencimento 20/12" (sem ano)
        Pattern.compile("(?is)\\b(?:venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?!\\s*[\\./-]\\s*\\d{4})"),
        // Ultra-flexível quando o PDF quebra dígitos: "Vencimento: 2 0 / 1 2 / 2 0 2 5"
        Pattern.compile("(?is)\\b(?:venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,60}([0-9][0-9\\s\\./-]{5,30})")
    );

    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu");

        // Ex.: "Titular: MARIANA OLIVEIRA" (variações comuns em PDFs)
        private static final Pattern HOLDER_PATTERN = Pattern.compile(
            "(?im)^\\s*(?:nome\\s+do\\s+titular|titular|cliente|nome)\\s*[:\\-]?\\s*(.+?)\\s*$"
        );

    // Ex.: 20/08 | PGTO. COBRANCA ... | BR | R$ -84,00
    // Ex.: 21/08 | WWW.STATUE... | TX | R$ 79,68
    private static final Pattern TX_LINE_PATTERN = Pattern.compile(
            "^(\\d{2}/\\d{2})\\s+(.+?)\\s+(?:([A-Z]{2})\\s+)?R\\$\\s*([-\\d.,]+)\\s*$"
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

        // IMPORTANT: vários bancos têm blocos "Resumo da fatura" / "Total desta fatura".
        // Para evitar falso-positivo (ex.: Itaú), exigimos marcadores de marca do BB.
        boolean hasBrandMarkers = n.contains("banco do brasil")
            || n.contains("ourocard")
            || n.contains("bb.com.br")
            || (n.contains("ouvidoria") && n.contains("bb"));
        if (!hasBrandMarkers) return false;

        // Sinais de layout para reduzir match em texto avulso.
        boolean hasTotalMarker = n.contains("total desta fatura") || n.contains("total da fatura");
        boolean hasInvoiceLayoutSignals =
            (n.contains("resumo da fatura") && hasTotalMarker)
                || (n.contains("pagamento efetuado") && n.contains("lancamentos atuais"));
        return hasInvoiceLayoutSignals;
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = normalizeNumericDates(text);
        Integer inferredYear = inferYearFromText(normalized);

        for (int i = 0; i < DUE_DATE_PATTERNS.size(); i++) {
            Pattern p = DUE_DATE_PATTERNS.get(i);
            Matcher m = p.matcher(normalized);
            if (!m.find()) continue;

            try {
                // Pattern 1: dia/mes/ano
                if (i == 0) {
                    Integer day = parseIntOrNull(m.group(1));
                    Integer month = parseIntOrNull(m.group(2));
                    Integer year = parseIntOrNull(m.group(3));
                    return safeDate(year, month, day);
                }

                // Pattern 2: dia/mes (sem ano)
                if (i == 1) {
                    Integer day = parseIntOrNull(m.group(1));
                    Integer month = parseIntOrNull(m.group(2));
                    Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
                    return safeDate(year, month, day);
                }

                // Pattern 3: dígitos soltos
                if (i == 2) {
                    String digits = safeTrim(m.group(1)).replaceAll("\\D", "");
                    if (digits.length() >= 8) {
                        Integer day = parseIntOrNull(digits.substring(0, 2));
                        Integer month = parseIntOrNull(digits.substring(2, 4));
                        Integer year = parseIntOrNull(digits.substring(4, 8));
                        return safeDate(year, month, day);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Última tentativa: se existir um dd/MM/yyyy após a palavra vencimento em texto não-normalizado
        // (caso raro onde a normalização atrapalhe)
        try {
            Matcher m = Pattern.compile("(?is)\\b(?:venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,30}(\\d{2}/\\d{2}/\\d{4})")
                    .matcher(text);
            if (m.find()) {
                return LocalDate.parse(m.group(1).trim(), DUE_DATE_FORMATTER);
            }
        } catch (Exception ignored) {
        }

        return null;
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

    private String normalizeNumericDates(String text) {
        if (text == null || text.isBlank()) return "";
        String t = text.replace('\u00A0', ' ');
        // Remove espaços entre dígitos ("2 2 / 1 2 / 2 0 2 5" -> "22/12/2025")
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
        // Normaliza espaços ao redor de separadores
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

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        try {
            if (year == null || month == null || day == null) return null;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
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
                // Linhas de detalhe (devem ser ignoradas): conversão em dólar, IOF, cotação, etc.
                if (isDetailLine(trimmed, nLine)) {
                    System.out.println("[BBParser] SKIPPED: " + trimmed + " (detail line)");
                }
                continue;
            }

            String ddmm = safeTrim(tx.group(1));
            String description = cleanTransactionDescription(safeTrim(tx.group(2)));
            String country = safeTrim(tx.group(3));
            String amountStr = safeTrim(tx.group(4));

            if (description.isEmpty()) continue;

            // Ignora pagamento de fatura (mês anterior/adiantamento) que aparece como transação na fatura.
            if (isPaymentFromPreviousInvoice(description)) {
                continue;
            }

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

            System.out.println("[BBParser] Extracted: " + description + " [" + td.amount + "] on " + td.date);

            if (!country.isEmpty() && !"BR".equalsIgnoreCase(country)) {
                skippingIntlContinuation = true;
            }
        }

        System.out.println("[BBParser] Total extracted: " + out.size() + " transactions");
        return out;
    }

    private boolean isPaymentFromPreviousInvoice(String description) {
        if (description == null || description.isBlank()) return false;

        // Normaliza para neutralizar pontuação: "PGTO. COBRANCA" -> "pgto cobranca"
        String n = normalizeForSearch(description)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // Ex.: "PGTO. COBRANCA 2958 ..."
        return n.startsWith("pgto cobranca")
                || n.startsWith("pagto cobranca")
                || n.startsWith("pagamento cobranca");
    }

    private boolean isDetailLine(String trimmed, String normalizedLower) {
        if (trimmed == null || trimmed.isBlank()) return false;
        if (normalizedLower == null) normalizedLower = normalizeForSearch(trimmed);

        if (trimmed.startsWith("***")) return true;

        // Exemplos: "Cotação do Dólar de 21/08: R$ 5,6915" / "Cotacao do Dolar ..."
        if (normalizedLower.startsWith("cotacao") || normalizedLower.startsWith("cotaçao")) {
            return true;
        }

        // IOF pode aparecer em linhas separadas em alguns layouts
        if (normalizedLower.startsWith("iof")) return true;

        return false;
    }

    private String cleanTransactionDescription(String description) {
        if (description == null) return "";
        String d = description.trim();
        if (d.isEmpty()) return d;

        // Alguns extratores colam as linhas de detalhe na mesma linha da transação.
        // Mantemos apenas a 1ª linha lógica: corta ao encontrar marcadores de detalhe.
        int cut = indexOfIgnoreCase(d, "***");
        if (cut >= 0) {
            d = d.substring(0, cut).trim();
        }

        cut = indexOfIgnoreCase(d, "COTAÇÃO");
        if (cut < 0) cut = indexOfIgnoreCase(d, "COTACAO");
        if (cut >= 0) {
            d = d.substring(0, cut).trim();
        }

        cut = indexOfIgnoreCase(d, " IOF");
        if (cut >= 0) {
            d = d.substring(0, cut).trim();
        }

        return d;
    }

    private int indexOfIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return -1;
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return h.indexOf(n);
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
}
