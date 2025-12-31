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

public class BradescoInvoiceParser implements InvoiceParserStrategy {

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|venc\\.)\\b\\s*[:\\-]?" +
                    "\\s*(\\d{2}\\s*/\\s*\\d{2}(?:\\s*/\\s*\\d{2,4})?)");

    private static final Pattern CARD_PATTERN = Pattern.compile(
            "(?is)\\bcart[ãa]o\\b\\s*[:\\-]?\\s*([^\\r\\n]+)");

        private static final Pattern HOLDER_PATTERN = Pattern.compile(
            "(?im)^\\s*titular\\s*[:\\-]?\\s*(.+?)\\s*$");

    private static final Pattern LAUNCH_LINE_PATTERN = Pattern.compile(
            "(?m)^(\\d{2}/\\d{2})(?:/\\d{2,4})?\\s+(.+?)\\s+(-?[\\d.,]+)\\s*$");


    private static final Pattern INSTALLMENT_PATTERN = Pattern.compile("(?i)\\bP/(\\d+)(?:\\s|$)");

    private static final DateTimeFormatter DUE_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = normalizeForSearch(text);
        boolean hasBank = n.contains("bradesco");
        boolean hasDue = n.contains("vencimento");
        boolean hasTotal = n.contains("total da fatura");
        boolean hasLaunch = n.contains("lancamentos") || n.contains("historico de lancamentos");
        return hasBank && hasDue && (hasTotal || hasLaunch);
    }
    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = DUE_DATE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                String raw = safeTrim(m.group(2));
                String cleaned = raw.replaceAll("\\s+", "");

                if (cleaned.matches("\\d{2}/\\d{2}/\\d{2}$")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 2) + "20" + cleaned.substring(cleaned.length() - 2);
                }

                if (cleaned.matches("\\d{2}/\\d{2}$")) {
                    int year = LocalDate.now().getYear();
                    LocalDate candidate = LocalDate.parse(cleaned + "/" + year, DUE_DATE_FORMATTER);
                    if (candidate.isBefore(LocalDate.now())) {
                        candidate = LocalDate.parse(cleaned + "/" + (year + 1), DUE_DATE_FORMATTER);
                    }
                    return candidate;
                }

                return LocalDate.parse(cleaned, DUE_DATE_FORMATTER);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();

        LocalDate dueDate = extractDueDate(text);
        String cardName = extractCardName(text);
        String holderName = extractHolderName(text);

        String section = extractLaunchesSection(text);

        List<TransactionData> out = new ArrayList<>();
        Matcher matcher = LAUNCH_LINE_PATTERN.matcher(section);
        while (matcher.find()) {
            String ddmm = safeTrim(matcher.group(1));
            String desc = safeTrim(matcher.group(2));
            String valueRaw = safeTrim(matcher.group(3));

            if (ddmm.isEmpty() || desc.isEmpty() || valueRaw.isEmpty()) continue;

            // NOVO: Ignorar linhas que são subtotais (Total para ...)
            if (desc.toUpperCase().startsWith("TOTAL PARA")) {
                continue;
            }

            // NOVO: Ignorar linhas que são pagamentos (débito em conta)
            String descUpper = desc.toUpperCase();
            if (descUpper.contains("PAGTO") || descUpper.contains("PAGAMENTO") ||
                    descUpper.contains("DEB EM C/C") || descUpper.contains("DÉBITO EM CONTA")) {
                continue;
            }

            BigDecimal amount = parseBrlAmount(valueRaw);
            if (amount == null) continue;

            TransactionType type = inferType(desc, amount);
            String category = categorize(desc, type);
            LocalDate purchaseDate = parsePurchaseDate(ddmm, dueDate);

            TransactionData td = new TransactionData(
                    desc,
                    amount.abs(),
                    type,
                    category,
                    purchaseDate,
                    cardName,
                    TransactionScope.PERSONAL
            );

            if (holderName != null && !holderName.isBlank()) {
                td.cardholderName = holderName;
            }

            applyInstallmentInfo(td, desc);
            out.add(td);
        }

        return out;
    }

    private String extractHolderName(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = HOLDER_PATTERN.matcher(text);
        if (m.find()) {
            String v = safeTrim(m.group(1));
            return v.isEmpty() ? null : v;
        }
        return null;
    }

    private String extractLaunchesSection(String text) {
        // Tentamos recortar a área relevante para reduzir falsos matches.
        String u = text.toUpperCase();

        int start = firstIndexOf(u,
                "LANÇAMENTOS",
                "LANCAMENTOS",
                "HISTÓRICO DE LANÇAMENTOS",
                "HISTORICO DE LANCAMENTOS");

        // Se não encontramos um marcador claro de lançamentos, NÃO recorte pelo "RESUMO".
        // Em muitos PDFs (especialmente via OCR) o texto pode conter "Resumo" no cabeçalho
        // antes de aparecerem os lançamentos, e esse recorte causava txCount=0.
        if (start < 0) {
            return text;
        }

        // NOVO: Procurar por "LIMITES" e parar antes dele
        // Isso evita que o limite utilizado seja importado como transação
        int limitesIndex = firstIndexOfFrom(u, start, "LIMITES");

        int end = firstIndexOfFrom(u, start,
            "RESUMO DA FATURA",
            "RESUMO");

        // Se encontramos "LIMITES", usar como marcador de fim (tem prioridade)
        // Isso garante que a seção de LIMITES é ignorada completamente
        if (limitesIndex > start && limitesIndex > 0) {
            end = limitesIndex;
        }

        if (end < 0 || end <= start) end = text.length();
        return text.substring(start, end);
    }

    private int firstIndexOf(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty() || needles == null) return -1;
        int best = -1;
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) continue;
            int idx = haystack.indexOf(needle);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private int firstIndexOfFrom(String haystack, int fromIndex, String... needles) {
        if (haystack == null || haystack.isEmpty() || needles == null) return -1;
        int start = Math.max(0, Math.min(fromIndex, haystack.length()));
        int best = -1;
        for (String needle : needles) {
            if (needle == null || needle.isBlank()) continue;
            int idx = haystack.indexOf(needle, start);
            if (idx >= 0 && (best < 0 || idx < best)) best = idx;
        }
        return best;
    }

    private String extractCardName(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = CARD_PATTERN.matcher(text);
        if (m.find()) {
            String card = safeTrim(m.group(1));
            if (!card.isEmpty()) return card;
        }
        return "Bradesco";
    }

    private void applyInstallmentInfo(TransactionData td, String desc) {
        if (td == null || desc == null || desc.isBlank()) return;
        Matcher m = INSTALLMENT_PATTERN.matcher(desc);
        if (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                if (num > 0) {
                    td.installmentNumber = num;
                    td.installmentTotal = 1;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String categorize(String description, TransactionType type) {
        String n = normalizeForSearch(description);

        // NOVO: Anuidade é uma taxa, não reembolso
        if (n.contains("anuidade")) {
            return "Taxas e Tarifas";
        }

        // NOVO: Compra de pontos é diverso
        if (n.contains("compra de pontos")) {
            return "Diversos";
        }

        // Créditos/cashback do Bradesco (muitas vezes vem como lançamento de crédito)
        if (type == TransactionType.INCOME) {
            if (n.contains("paygoal") || n.contains("cashback") || n.contains("pontos") || n.contains("reembolso")
                    || n.contains("credito") || n.contains("devolucao") || n.contains("anuidade")) {
                return "Reembolso";
            }
            return MerchantCategoryMapper.categorize(description, type);
        }

        // Heurística Bradesco: linhas CTCE
        if (n.contains("ctce")) {
            return "Hospedagem";
        }

        // Compras internacionais/IOF tendem a ser de viagem
        if (n.contains("exterior") || n.contains("iof") || n.contains("trans. exterior") || n.contains("trans exterior")) {
            return "Viagem";
        }

        return MerchantCategoryMapper.categorize(description, type);
    }

    private TransactionType inferType(String description, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.INCOME;
        }

        String n = normalizeForSearch(description);
        // NOVO: Anuidade é SEMPRE EXPENSE, não INCOME
        if (n.contains("anuidade")) {
            return TransactionType.EXPENSE;
        }

        // NOVO: Compra de pontos é SEMPRE EXPENSE, não INCOME
        if (n.contains("compra de pontos")) {
            return TransactionType.EXPENSE;
        }

        if (n.contains("reembolso") || n.contains("credito") || n.contains("devolucao")
                || n.contains("cashback") || n.contains("paygoal")) {
            return TransactionType.INCOME;
        }

        return TransactionType.EXPENSE;
    }

    private BigDecimal parseBrlAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        s = s.replace("R$", "").replace(" ", "").trim();
        // padrão BR: ponto milhar, vírgula decimal
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

    private LocalDate parsePurchaseDate(String ddmm, LocalDate dueDate) {
        if (ddmm == null) return null;
        try {
            int day = Integer.parseInt(ddmm.substring(0, 2));
            int month = Integer.parseInt(ddmm.substring(3, 5));
            int year = (dueDate != null ? dueDate.getYear() : LocalDate.now().getYear());

            // Se o mês da compra for "futuro" em relação ao vencimento, assume ano anterior (virada de ano)
            if (dueDate != null && month > dueDate.getMonthValue()) {
                year = year - 1;
            }

            LocalDate base = LocalDate.of(year, month, 1);
            int dom = Math.min(day, base.lengthOfMonth());
            return base.withDayOfMonth(dom);
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        String noAccents = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }
}


