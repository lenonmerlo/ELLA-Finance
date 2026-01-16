package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
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

public class NubankInvoiceParser implements InvoiceParserStrategy {

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
            "(?i)Data de vencimento:\\s*(\\d{2})\\s+([A-Z]{3})\\s+(\\d{4})"
    );

        // Ex.: 06 NOV    🔄    Pepay*Segurofatura    R$ 6,90
        // Ex.: 12 NOV    🏪    R F CRUZ CHURRASCANAL    R$ 104,30
        // Observação: alguns extratores variam o número de colunas/ícones; capturamos o miolo e depois removemos ícones.
        private static final Pattern TX_LINE_PATTERN = Pattern.compile(
            "(?mi)^(\\d{2})\\s+([A-Z0-9]{3})\\s+(.+?)\\s+R\\$\\s*([\\d.]+,\\d{2})(?:\\s+.*)?$"
        );

        // Linha principal sem data (a data está na linha anterior): <DESCRICAO> R$ <VALOR>
        // IMPORTANTE: exclui linhas de detalhe como "↳ Total a pagar".
        private static final Pattern TX_NO_DATE_PATTERN = Pattern.compile(
            "(?mi)^(?![↳└]).+?\\s+R\\$\\s*([\\d.]+,\\d{2})(?:\\s+.*)?$"
        );

    // Ex.: Pagamento em 05 NOV: -R$ 934,83
    private static final Pattern PAYMENT_LINE_PATTERN_A = Pattern.compile(
            "(?mi)^Pagamento em\\s+(\\d{2})\\s+([A-Z]{3})\\s*:?\\s*-R\\$\\s*([\\d.]+,\\d{2})\\s*$"
    );

    // Ex.: 05 NOV    Pagamento em 05 NOV    -R$ 934,83
    private static final Pattern PAYMENT_LINE_PATTERN_B = Pattern.compile(
            "(?mi)^(\\d{2})\\s+([A-Z]{3}).*?\\bPagamento em\\b.*?-R\\$\\s*([\\d.]+,\\d{2})\\s*$"
    );

    private static final Pattern TOTAL_E_PAGAR_PATTERN = Pattern.compile(
            "(?is)Total e pagar:\\s*R\\$\\s*([\\d.]+,\\d{2})"
    );

        private static final Pattern TOTAL_A_PAGAR_PATTERN = Pattern.compile(
            "(?is)Total a pagar:\\s*R\\$\\s*([\\d.]+,\\d{2})"
        );

    private static final Map<String, Integer> MONTHS = buildMonths();

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);

        // Evita falso-positivo: vários bancos contêm "data de vencimento", "transações" e "fatura".
        // Nubank costuma trazer identificadores claros da marca (ex.: "Nubank" / "Nu Pagamentos").
        boolean hasBrandMarkers = n.contains("nubank")
            || n.contains("nu pagamentos")
            || n.contains("nucard")
            || n.contains("nu bank");
        if (!hasBrandMarkers) return false;

        return n.contains("data de vencimento")
            && n.contains("transacoes")
            && (n.contains("esta e a sua fatura") || n.contains("fatura"));
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = DUE_DATE_PATTERN.matcher(text);
        if (!m.find()) return null;

        Integer day = parseIntOrNull(m.group(1));
        String mon = safeTrim(m.group(2));
        Integer year = parseIntOrNull(m.group(3));
        if (day == null || year == null || mon.isEmpty()) return null;

        Integer month = monthToNumber(mon);
        if (month == null) return null;
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) {
            // mantém consistência com outros parsers: não inventa vencimento
            return Collections.emptyList();
        }

        List<TransactionData> out = new ArrayList<>();

        // 1) Transações (despesas)
        // Observação: alguns extratores quebram a linha da transação (descrição em uma linha, valor em outra),
        // adicionam sufixos após o valor, ou trocam letras por dígitos no mês (ex.: N0V). Fazemos parsing linha-a-linha.
        String[] lines = text.split("\\r?\\n");

        record Pending(String day, String monthAbbrev, String description) {}
        record DateAnchor(String day, String monthAbbrev) {}
        Pending pending = null;
        DateAnchor lastDateAnchor = null;

        Pattern amountOnlyPattern = Pattern.compile("(?i)^R\\$\\s*([\\d.]+,\\d{2})\\s*$");
        Pattern amountNoCurrencyOnlyPattern = Pattern.compile("^([\\d.]+,\\d{2})\\s*$");
        Pattern txHeaderPattern = Pattern.compile("(?i)^(\\d{2})\\s+([A-Z0-9]{3})\\s+(.+?)\\s*$");
        Pattern dateOnlyPattern = Pattern.compile("(?i)^(\\d{2})\\s+([A-Z0-9]{3})\\s*$");

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            String nLine = normalizeForSearch(line);

            // Ignora linhas de detalhe "↳ Total a pagar" / "Total e pagar".
            if (line.startsWith("↳") || nLine.contains("total a pagar") || nLine.contains("total e pagar")) {
                System.out.println("[NubankParser] SKIPPED: " + line + " (Total a pagar detail line)");
                continue;
            }

            // Âncora de data em linha isolada (comum em layouts que quebram a linha da transação)
            Matcher dateOnly = dateOnlyPattern.matcher(line);
            if (dateOnly.matches()) {
                lastDateAnchor = new DateAnchor(dateOnly.group(1), dateOnly.group(2));
                continue;
            }

            // se havia uma transação pendente, tenta consumir uma linha de valor
            if (pending != null) {
                Matcher amountOnly = amountOnlyPattern.matcher(line);
                Matcher amountNoCurrencyOnly = amountNoCurrencyOnlyPattern.matcher(line);
                boolean currencyMatched = amountOnly.matches();
                boolean noCurrencyMatched = amountNoCurrencyOnly.matches();
                if (currencyMatched || noCurrencyMatched) {
                    String amountStr = currencyMatched ? amountOnly.group(1) : amountNoCurrencyOnly.group(1);
                    String mon = pending.monthAbbrev.replace('0', 'O');
                    LocalDate purchaseDate = buildPurchaseDate(pending.day, mon, dueDate);
                    BigDecimal amount = parseBrlAmount(amountStr);
                    String desc = stripLeadingIconTokens(safeTrim(pending.description));
                    if (purchaseDate != null && amount != null && !desc.isEmpty()) {
                        TransactionType type = TransactionType.EXPENSE;
                        String category = categorizeForNubank(desc, type);
                        TransactionData td = new TransactionData(
                                desc,
                                amount.abs(),
                                type,
                                category,
                                purchaseDate,
                                null,
                                TransactionScope.PERSONAL
                        );
                        out.add(td);
                        System.out.println("[NubankParser] Extracted: " + desc + " [" + td.amount + "] on " + td.date);
                    } else {
                        System.out.println("[NubankParser] SKIPPED: " + pending.description + " (pending header could not be finalized)");
                    }
                    pending = null;
                    continue;
                } else {
                    // não era linha de valor; descarta a pendência
                    System.out.println("[NubankParser] SKIPPED: " + pending.description + " (missing amount line)");
                    pending = null;
                }
            }

            Matcher txMatcher = TX_LINE_PATTERN.matcher(line);
            if (txMatcher.find()) {
                String dayStr = txMatcher.group(1);
                String monStr = txMatcher.group(2);
                String descRaw = txMatcher.group(3);
                String amountStr = txMatcher.group(4);

                // atualiza âncora (para casos onde as próximas linhas dependem dela)
                lastDateAnchor = new DateAnchor(dayStr, monStr);

                String monNormalized = safeTrim(monStr).replace('0', 'O');
                LocalDate purchaseDate = buildPurchaseDate(dayStr, monNormalized, dueDate);
                if (purchaseDate == null) {
                    System.out.println("[NubankParser] SKIPPED: " + line + " (invalid purchaseDate)");
                    continue;
                }

                String desc = stripLeadingIconTokens(safeTrim(descRaw));
                BigDecimal amount = parseBrlAmount(amountStr);
                if (desc.isEmpty() || amount == null) {
                    System.out.println("[NubankParser] SKIPPED: " + line + " (missing desc/amount)");
                    continue;
                }

                TransactionType type = TransactionType.EXPENSE;
                String category = categorizeForNubank(desc, type);

                TransactionData td = new TransactionData(
                        desc,
                        amount.abs(),
                        type,
                        category,
                        purchaseDate,
                        null,
                        TransactionScope.PERSONAL
                );
                out.add(td);
                System.out.println("[NubankParser] Extracted: " + desc + " [" + td.amount + "] on " + td.date);
                continue;
            }

            // Linha principal sem data: <DESCRICAO> R$ <VALOR> (ancora na data anterior)
            if (lastDateAnchor != null) {
                Matcher noDate = TX_NO_DATE_PATTERN.matcher(line);
                if (!noDate.find()) {
                    // segue fluxo normal
                } else {

                    BigDecimal amount = parseBrlAmount(noDate.group(1));
                    if (amount == null) {
                        System.out.println("[NubankParser] SKIPPED: " + line + " (invalid amount)");
                        continue;
                    }

                    int idx = line.toUpperCase(Locale.ROOT).lastIndexOf("R$");
                    String descRaw = idx > 0 ? line.substring(0, idx).trim() : line;
                    String desc = stripLeadingIconTokens(safeTrim(descRaw));
                    if (desc.isEmpty()) {
                        System.out.println("[NubankParser] SKIPPED: " + line + " (missing description)");
                        continue;
                    }

                    String mon = lastDateAnchor.monthAbbrev.replace('0', 'O');
                    LocalDate purchaseDate = buildPurchaseDate(lastDateAnchor.day, mon, dueDate);
                    if (purchaseDate == null) {
                        System.out.println("[NubankParser] SKIPPED: " + line + " (invalid purchaseDate)");
                        continue;
                    }

                    TransactionType type = TransactionType.EXPENSE;
                    String category = categorizeForNubank(desc, type);
                    TransactionData td = new TransactionData(
                            desc,
                            amount.abs(),
                            type,
                            category,
                            purchaseDate,
                            null,
                            TransactionScope.PERSONAL
                    );
                    out.add(td);
                    System.out.println("[NubankParser] Extracted: " + desc + " [" + td.amount + "] on " + td.date);
                    continue;
                }
            }

            // tentativa: linha de transação sem valor (valor na linha seguinte)
            Matcher header = txHeaderPattern.matcher(line);
            if (header.find()) {
                String maybeDesc = safeTrim(header.group(3));
                String maybeDescNorm = normalizeForSearch(maybeDesc);
                if (!maybeDescNorm.contains("pagamento em") && !maybeDescNorm.contains("pagamentos") && !maybeDescNorm.contains("fatura")) {
                    lastDateAnchor = new DateAnchor(header.group(1), header.group(2));
                    pending = new Pending(header.group(1), header.group(2), header.group(3));
                    continue;
                }
            }

            // logs de debug para linhas candidatas que parecem transação mas não bateram
            boolean looksLikeTx = line.matches("^\\d{2}\\s+\\S{3}.*");
            boolean hasAmount = line.contains("R$") || line.matches(".*\\d+[\\.,]\\d{2}.*");
            if (looksLikeTx && hasAmount) {
                System.out.println("[NubankParser] SKIPPED: " + line);
            }
        }

        // 2) Pagamentos (créditos)
        addPayments(text, dueDate, out);

        System.out.println("[NubankParser] Total extracted: " + out.size() + " transactions");

        return out;
    }

    private BigDecimal extractTotalToPay(String line) {
        if (line == null || line.isBlank()) return null;

        Matcher m1 = TOTAL_A_PAGAR_PATTERN.matcher(line);
        if (m1.find()) {
            return parseBrlAmount(m1.group(1));
        }
        Matcher m2 = TOTAL_E_PAGAR_PATTERN.matcher(line);
        if (m2.find()) {
            return parseBrlAmount(m2.group(1));
        }

        return null;
    }

    private BigDecimal extractTotalAPagar(String line) {
        if (line == null || line.isBlank()) return null;
        Matcher m = TOTAL_A_PAGAR_PATTERN.matcher(line);
        if (m.find()) {
            return parseBrlAmount(m.group(1));
        }
        return null;
    }

    private void addPayments(String text, LocalDate dueDate, List<TransactionData> out) {
        if (text == null || text.isBlank()) return;

        // preferimos A (quando a linha começa com "Pagamento em") e depois B (quando vem em colunas com data)
        addPaymentsByPattern(text, dueDate, out, PAYMENT_LINE_PATTERN_A, true);
        addPaymentsByPattern(text, dueDate, out, PAYMENT_LINE_PATTERN_B, false);
    }

    private void addPaymentsByPattern(
            String text,
            LocalDate dueDate,
            List<TransactionData> out,
            Pattern p,
            boolean hasNoLeadingDate
    ) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String dayStr;
            String monStr;
            String amountStr;

            if (hasNoLeadingDate) {
                dayStr = m.group(1);
                monStr = m.group(2);
                amountStr = m.group(3);
            } else {
                dayStr = m.group(1);
                monStr = m.group(2);
                amountStr = m.group(3);
            }

            LocalDate purchaseDate = buildPurchaseDate(dayStr, monStr, dueDate);
            BigDecimal amount = parseBrlAmount(amountStr);
            if (purchaseDate == null || amount == null) continue;

            String desc = "Pagamento em " + dayStr + " " + monStr;
            TransactionData td = new TransactionData(
                    desc,
                    amount.abs(),
                    TransactionType.INCOME,
                    "Reembolso",
                    purchaseDate,
                    null,
                    TransactionScope.PERSONAL
            );
            out.add(td);
        }
    }

    private LocalDate buildPurchaseDate(String dayStr, String monStr, LocalDate dueDate) {
        Integer day = parseIntOrNull(dayStr);
        Integer month = monthToNumber(monStr);
        if (day == null || month == null) return null;
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

    private Integer monthToNumber(String mon) {
        if (mon == null) return null;
        String key = mon.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return null;
        return MONTHS.get(key);
    }

    private static Map<String, Integer> buildMonths() {
        Map<String, Integer> m = new HashMap<>();
        m.put("JAN", 1);
        m.put("FEV", 2);
        m.put("MAR", 3);
        m.put("ABR", 4);
        m.put("MAI", 5);
        m.put("JUN", 6);
        m.put("JUL", 7);
        m.put("AGO", 8);
        m.put("SET", 9);
        m.put("OUT", 10);
        m.put("NOV", 11);
        m.put("DEZ", 12);
        return m;
    }

    private BigDecimal parseBrlAmount(String value) {
        try {
            if (value == null) return null;
            String v = value.trim();
            if (v.isEmpty()) return null;
            v = v.replace("R$", "").replace(" ", "");
            boolean negative = v.startsWith("-");
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

    private String categorizeForNubank(String description, TransactionType type) {
        if (type == TransactionType.INCOME) {
            return "Reembolso";
        }

        String n = normalizeForSearch(description);
        String upper = description == null ? "" : description.toUpperCase(Locale.ROOT);

        // Seguro
        if (n.contains("pepay") || n.contains("segurofatura") || n.contains("seguro")) {
            return "Seguro";
        }

        // Transporte
        if (upper.contains("UBER") || upper.contains("99") || upper.contains("99TAXI") || upper.contains("TAXI")
                || upper.contains("BOLT") || upper.contains("LOGGI")) {
            return "Transporte";
        }

        // Delivery
        if (upper.contains("IFOOD") || upper.startsWith("IFD") || upper.contains("DELIVERY")) {
            return "iFood";
        }

        // Saúde
        if (upper.contains("FARMACIA") || upper.contains("FARMÁCIA") || upper.contains("DROGARIA")
                || upper.contains("HOSPITAL") || upper.contains("CLINICA") || upper.contains("MÉDICO")
                || upper.contains("MEDICO") || upper.contains("ODONTO") || upper.contains("DENTISTA")) {
            return "Saúde";
        }

        // Alimentação (restaurantes / bares / similares) - Nubank guia
        if (upper.contains("RESTAURANTE") || upper.contains("BAR ") || upper.startsWith("BAR")
                || upper.contains("PIZZARIA") || upper.contains("BURGER") || upper.contains("SUSHI")
                || upper.contains("PADARIA") || upper.contains("CONFEITARIA") || upper.contains("CHURRASC")
                || upper.contains("LANCHONETE") || upper.contains("CAFÉ") || upper.contains("CAFE")
                || upper.contains("BELMONTE") || upper.contains("BAFO")) {
            return "Alimentação";
        }

        // Assinaturas / software
        if (upper.contains("GOOGLE") || upper.contains("MICROSOFT") || upper.contains("ADOBE")
                || upper.contains("NETFLIX") || upper.contains("SPOTIFY") || upper.contains("AMAZON")
                || upper.contains("APPLE") || upper.contains("DROPBOX") || upper.contains("FIGMA")
                || upper.contains("NOTION") || upper.contains("CANVA") || upper.contains("BRASIL PAGAMENTOS")) {
            return "Assinaturas";
        }

        // Lazer
        if (upper.contains("PARQUE") || upper.contains("CINEMA") || upper.contains("TEATRO")
                || upper.contains("MUSEU") || upper.contains("ENTRETENIMENTO") || upper.contains("DIVERSAO")
                || upper.contains("JOGO") || upper.contains("GAME")) {
            return "Lazer";
        }

        return MerchantCategoryMapper.categorize(description, type);
    }

    private String stripLeadingIconTokens(String desc) {
        if (desc == null) return "";
        String d = desc.trim();
        if (d.isEmpty()) return d;

        // Remove até 3 tokens iniciais caso sejam apenas símbolos/ícones (ex.: 🔄, 💳, 🏪, ↳, └→)
        for (int i = 0; i < 3; i++) {
            String[] parts = d.split("\\s+", 2);
            if (parts.length == 0) break;
            String first = parts[0];
            if (!isSymbolOnlyToken(first)) break;
            d = (parts.length == 2) ? parts[1].trim() : "";
            if (d.isEmpty()) break;
        }
        return d;
    }

    private boolean isSymbolOnlyToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.isEmpty()) return false;
        // Evita remover algo que contenha letras/dígitos
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetterOrDigit(t.charAt(i))) return false;
        }
        // Tokens muito longos provavelmente não são ícones (ex.: parte do merchant)
        return t.length() <= 4;
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static final class SpanMatch {
        final int start;
        final int end;
        final String day;
        final String monthAbbrev;
        final String description;
        final String amount;

        SpanMatch(int start, int end, String day, String monthAbbrev, String description, String amount) {
            this.start = start;
            this.end = end;
            this.day = day;
            this.monthAbbrev = monthAbbrev;
            this.description = description;
            this.amount = amount;
        }
    }
}
