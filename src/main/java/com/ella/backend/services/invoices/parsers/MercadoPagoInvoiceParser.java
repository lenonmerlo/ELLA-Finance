package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class MercadoPagoInvoiceParser implements InvoiceParserStrategy {

    @Override
    public boolean isApplicable(String text) {
        if (text == null) return false;
        String n = normalizeForSearch(text);
        return n.contains("mercado pago") || n.contains("mp card");
    }

    @Override
    public LocalDate extractDueDate(String text) {
        if (text == null || text.isBlank()) return null;

        // Prioriza o "Vencimento:" explícito; depois tenta "Vence em".
        Pattern p1 = Pattern.compile("(?is)\\bvencimento\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return parseDueDate(m1.group(1));
        }

        Pattern p2 = Pattern.compile("(?is)\\bvence\\s+em\\b\\s*[:\\-]?\\s*(\\d{2}/\\d{2}/\\d{4})");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return parseDueDate(m2.group(1));
        }

        return null;
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        if (text == null || text.isBlank()) return List.of();

        LocalDate dueDate = extractDueDate(text);

        List<TransactionData> out = new ArrayList<>();

        Pattern cardHeader = Pattern.compile("(?i)cart[aã]o\\s+([a-z]+)\\s*\\[.*?(\\d{4})\\s*\\]");

        // Linhas típicas (PDFBox costuma remover os pipes)
        Pattern installmentLine = Pattern.compile(
                "(?i)^(\\d{2}/\\d{2})\\s+(.+?)\\s+parcela\\s+(\\d+)\\s+de\\s+(\\d+)\\s+R\\$\\s*([\\d\\.]+,\\d{2})\\s*$");
        Pattern basicLine = Pattern.compile(
                "(?i)^(\\d{2}/\\d{2})\\s+(.+?)\\s+R\\$\\s*([\\-]?[\\d\\.]+,\\d{2})\\s*$");

        Pattern intlStart = Pattern.compile("(?i)^(\\d{2}/\\d{2})\\s+compra\\s+internacional\\s+em\\s+(.+?)\\s*$");
        Pattern brlAmountLine = Pattern.compile("(?i).*R\\$\\s*([\\-]?[\\d\\.]+,\\d{2}).*");

        String currentCardName = null;
        String pendingIntlDate = null;
        String pendingIntlDesc = null;

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) continue;

            Matcher ch = cardHeader.matcher(line);
            if (ch.find()) {
                String brand = ch.group(1);
                String last4 = ch.group(2);
                currentCardName = (brand != null ? brand.trim() : "") + " " + (last4 != null ? last4.trim() : "");
                continue;
            }

            // Compra internacional (3 linhas): captura início e depois pega o valor final em BRL.
            Matcher intl = intlStart.matcher(line);
            if (intl.find()) {
                pendingIntlDate = intl.group(1);
                pendingIntlDesc = "Compra internacional em " + intl.group(2);
                continue;
            }

            if (pendingIntlDate != null) {
                Matcher am = brlAmountLine.matcher(line);
                if (am.matches()) {
                    BigDecimal amount = parseBrlAmount(am.group(1));
                    if (amount != null) {
                        TransactionType type = inferType(pendingIntlDesc, amount);
                        TransactionData td = new TransactionData(
                                pendingIntlDesc,
                                amount.abs(),
                                type,
                                "Outros",
                                parsePurchaseDate(pendingIntlDate, dueDate),
                                currentCardName,
                                TransactionScope.PERSONAL
                        );
                        out.add(td);
                    }
                    pendingIntlDate = null;
                    pendingIntlDesc = null;
                }
                continue;
            }

            Matcher mi = installmentLine.matcher(line);
            if (mi.find()) {
                String ddmm = mi.group(1);
                String desc = mi.group(2);
                int instNum = Integer.parseInt(mi.group(3));
                int instTot = Integer.parseInt(mi.group(4));
                BigDecimal amount = parseBrlAmount(mi.group(5));
                if (amount == null) continue;

                TransactionType type = inferType(desc, amount);
                TransactionData td = new TransactionData(
                        desc,
                        amount.abs(),
                        type,
                        "Outros",
                        parsePurchaseDate(ddmm, dueDate),
                        currentCardName,
                        TransactionScope.PERSONAL
                );
                td.installmentNumber = instNum;
                td.installmentTotal = instTot;
                out.add(td);
                continue;
            }

            Matcher mb = basicLine.matcher(line);
            if (mb.find()) {
                String ddmm = mb.group(1);
                String desc = mb.group(2);
                BigDecimal amount = parseBrlAmount(mb.group(3));
                if (amount == null) continue;

                TransactionType type = inferType(desc, amount);
                TransactionData td = new TransactionData(
                        desc,
                        amount.abs(),
                        type,
                        "Outros",
                        parsePurchaseDate(ddmm, dueDate),
                        currentCardName,
                        TransactionScope.PERSONAL
                );
                out.add(td);
            }
        }

        return out;
    }

    private String normalizeForSearch(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private LocalDate parseDueDate(String value) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBrlAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // remove separador de milhar e converte decimal
        s = s.replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private TransactionType inferType(String description, BigDecimal amount) {
        String d = description == null ? "" : description.toLowerCase();
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return TransactionType.INCOME;
        }
        if (d.contains("pagamento da fatura") || d.contains("pagamento")) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private LocalDate parsePurchaseDate(String ddmm, LocalDate dueDate) {
        if (ddmm == null) return null;
        try {
            int day = Integer.parseInt(ddmm.substring(0, 2));
            int month = Integer.parseInt(ddmm.substring(3, 5));
            int year = (dueDate != null ? dueDate.getYear() : LocalDate.now().getYear());

            // Se for uma fatura de janeiro e a compra for em dezembro, assume ano anterior.
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
}
