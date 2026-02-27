package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.util.NormalizeUtil;

/**
 * Parser específico para faturas Itaú LATAM PASS.
 *
 * - Seleção por heurística robusta em texto extraído (PDFBox/OCR)
 * - Parsing principal via PDF (ella-extractor /parse/itau-latam-pass)
 */
public class ItauLatamPassInvoiceParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(ItauLatamPassInvoiceParser.class);

    private static final String BANK_LABEL = "Itau LATAM PASS";

    private static final Pattern HAS_LAUNCHES_SECTION = Pattern.compile(
            "(?is)(?:lan(?:c|ç)?amentos\\s*(?:[:：]?\\s*)?compras\\s+e\\s+saques|lan(?:c|ç)?amentos\\s*(?:[:：]?\\s*)?produtos\\s+e\\s+servi)"
    );

    private static final Pattern ITAU_TX_LINE = Pattern.compile(
            "(?m)^(\\d{2})/(\\d{2})\\s+(.+?)\\s+(-?\\d{1,3}(?:\\.\\d{3})*,\\d{2})\\s*$"
    );

    private static final Pattern CARD_5300 = Pattern.compile("(?is)cart[aã]o\\s*5300");

    private final EllaExtractorLatamPassClient client;
    private final ItauInvoiceParser fallbackTextParser = new ItauInvoiceParser();

    public ItauLatamPassInvoiceParser() {
        this(new EllaExtractorLatamPassClient());
    }

    ItauLatamPassInvoiceParser(EllaExtractorLatamPassClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;

        String n = NormalizeUtil.normalize(text);

        // Must NOT steal Personalité/Personnalité.
        if (n.contains("personalite") || n.contains("personnalite")) {
            return false;
        }

        boolean hasItauMarker = n.contains("itau")
                || n.contains("banco itau")
                || n.contains("itau unibanco")
                || n.contains("ita unibanco")
                || n.contains("unibanco")
                || n.contains("itaucard")
                || n.contains("itau card")
                || n.contains("ita cares")
                || n.contains("itau cares")
                || n.contains("itacares")
                || n.contains("itaucares");

        boolean hasLaunchesSection = HAS_LAUNCHES_SECTION.matcher(n).find();

        // IMPORTANT: PDFs like the one you pasted often don't contain the literal words "LATAM PASS".
        // Instead, we detect the LATAM PASS layout by a combination of strong signals.
        boolean hasLatamCommerceSignals = n.contains("latam air")
                || n.contains("latam dcp")
                || n.contains("smiles")
                || n.contains("smiles fidel")
                || n.contains("smiles fid");

        boolean hasPaymentsSection = n.contains("pagamentos efetuados");
        boolean hasFutureInstallmentsSection = n.contains("compras parceladas")
                && (n.contains("proximas faturas") || n.contains("pr ximas faturas") || n.contains("proxima fatura"));
        boolean hasCardBinSignal = CARD_5300.matcher(n).find() || n.contains("5300.xxxx") || n.contains("5300 xxxx");

        int signals = 0;
        if (hasPaymentsSection) signals++;
        if (hasFutureInstallmentsSection) signals++;
        if (hasCardBinSignal) signals++;

        // Require LATAM signal + at least 2 layout signals to avoid stealing regular Itaú invoices
        // that merely contain a merchant like "LATAM".
        return hasItauMarker && hasLaunchesSection && hasLatamCommerceSignals && signals >= 2;

    }

    @Override
    public LocalDate extractDueDate(String text) {
        // Reuse robust Itaú due date extraction.
        return fallbackTextParser.extractDueDate(text);
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        // Keep selection score aligned with Itaú generic parser (so this parser can win when applicable).
        // The pipeline will call parseWithPdf for the real parsing.
        // The pipeline will call parseWithPdf for the real parsing, but the selector uses txCount
        // to rank candidates. Provide a lightweight extraction that matches this layout and
        // excludes demonstrative payments.
        return extractTransactionsForScoring(text);
    }

    private List<TransactionData> extractTransactionsForScoring(String text) {
        if (text == null || text.isBlank()) return List.of();

        LocalDate dueDate = extractDueDate(text);
        if (dueDate == null) {
            // Without a due date we can still return a few parsed lines, but year inference will be weaker.
            // Keep it conservative.
            dueDate = LocalDate.now();
        }

        String n = text.replace('\u00A0', ' ');

        // Only consider lines after the "Lançamentos" section starts, to avoid boleto/metadata noise.
        int startIdx = indexOfIgnoreCase(n, "Lançamentos: compras e saques");
        if (startIdx < 0) startIdx = indexOfIgnoreCase(n, "Lancamentos: compras e saques");
        if (startIdx < 0) startIdx = 0;

        String tail = n.substring(startIdx);

        // Stop before "Compras parceladas - próximas faturas" to avoid future installments.
        int endIdx = indexOfIgnoreCase(tail, "Compras parceladas - próximas faturas");
        if (endIdx < 0) endIdx = indexOfIgnoreCase(tail, "Compras parceladas - proximas faturas");
        if (endIdx > 0) {
            tail = tail.substring(0, endIdx);
        }

        List<TransactionData> out = new ArrayList<>();
        Matcher m = ITAU_TX_LINE.matcher(tail);
        while (m.find()) {
            int day = safeParseInt(m.group(1));
            int month = safeParseInt(m.group(2));
            if (day <= 0 || month <= 0) continue;

            String desc = normalizeSpaces(m.group(3));
            if (desc == null || desc.isBlank()) continue;
            if (isDemonstrativePayment(desc)) continue;

            BigDecimal amount = parsePtBrMoneyOrNull(m.group(4));
            if (amount == null) continue;

            // For credit card invoices: charges/fees are EXPENSE, credits/estornos are INCOME.
            // Amount sign is not reliable here (some extractors may emit debits as negative).
            String nd = NormalizeUtil.normalize(desc);
            TransactionType type = isRefundOrCreditTransaction(nd)
                    ? TransactionType.INCOME
                    : TransactionType.EXPENSE;
            amount = amount.abs();

            LocalDate date = inferYearFromDueDate(dueDate, day, month);

            String category = MerchantCategoryMapper.categorize(desc, type);
            TransactionScope scope = TransactionScope.PERSONAL;
            TransactionData td = new TransactionData(desc, amount, type, category, date, BANK_LABEL, scope);
            out.add(td);
        }
        return out;
    }

    private static LocalDate inferYearFromDueDate(LocalDate dueDate, int day, int month) {
        int year = dueDate.getYear();
        // If the transaction month is after the invoice due month, it's usually previous year.
        if (month > dueDate.getMonthValue()) {
            year = year - 1;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return dueDate;
        }
    }

    private static BigDecimal parsePtBrMoneyOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            String v = s.trim().replace(".", "").replace(",", ".");
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return -1;
        return haystack.toLowerCase().indexOf(needle.toLowerCase());
    }

    @Override
    public ParseResult parseWithPdf(byte[] pdfBytes, String extractedText) {
        EllaExtractorLatamPassClient.ItauLatamPassResponse resp = client.parseItauLatamPass(pdfBytes);
        if (resp == null || resp.transactions() == null) {
            throw new IllegalStateException("ella-extractor returned null response");
        }

        LocalDate dueDate = parseIsoDateOrNull(resp.dueDate());

        BigDecimal totalAmount = null;
        try {
            totalAmount = resp.total() == null ? null : BigDecimal.valueOf(resp.total());
        } catch (Exception ignored) {
            totalAmount = null;
        }

        List<TransactionData> out = new ArrayList<>();
        for (EllaExtractorLatamPassClient.ItauLatamPassResponse.Tx tx : resp.transactions()) {
            if (tx == null) continue;

            String desc = normalizeSpaces(tx.description());
            if (desc == null || desc.isBlank()) continue;

            // Pagamentos (ex.: "PAGAMENTO PIX") são bloco demonstrativo e não devem virar lançamento.
            if (isDemonstrativePayment(desc)) {
                continue;
            }

            BigDecimal amount;
            try {
                amount = tx.amount() == null ? null : BigDecimal.valueOf(tx.amount());
            } catch (Exception ignored) {
                amount = null;
            }
            if (amount == null) continue;

            String nd = NormalizeUtil.normalize(desc);
            TransactionType type = isRefundOrCreditTransaction(nd)
                    ? TransactionType.INCOME
                    : TransactionType.EXPENSE;
            amount = amount.abs();

            LocalDate txDate = parseIsoDateOrNull(tx.date());
            if (txDate == null) continue;

            Integer instNum = null;
            Integer instTot = null;
            if (tx.installment() != null) {
                instNum = tx.installment().current();
                instTot = tx.installment().total();
            }
            if (instNum == null) instNum = tx.installmentCurrent();
            if (instTot == null) instTot = tx.installmentTotal();

            String category = MerchantCategoryMapper.categorize(desc, type);
            TransactionScope scope = TransactionScope.PERSONAL;

            TransactionData td = new TransactionData(
                    desc,
                    amount,
                    type,
                    category,
                    txDate,
                    BANK_LABEL,
                    scope
            );
            td.installmentNumber = instNum;
            td.installmentTotal = instTot;
            td.setDueDate(dueDate);
            out.add(td);
        }

        return ParseResult.builder()
                .transactions(out)
                .dueDate(dueDate)
                .totalAmount(totalAmount)
                .bankName(BANK_LABEL)
                .build();
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean isRefundOrCreditTransaction(String normalizedDescription) {
        if (normalizedDescription == null || normalizedDescription.isBlank()) return false;

        // Strong signal.
        if (normalizedDescription.contains("estorno")) return true;

        // "credito" is ambiguous on invoices (e.g. "credito rotativo", "limite de credito").
        if (!normalizedDescription.contains("credito")) return false;

        // Exclude common contextual phrases that are not a credit/refund transaction.
        if (normalizedDescription.contains("credito rotativo")) return false;
        if (normalizedDescription.contains("limite de credito")) return false;
        if (normalizedDescription.contains("credito/atraso")) return false;

        // Keep it conservative: treat only standalone word "credito" as a credit-like transaction.
        return Pattern.compile("\\bcredito\\b").matcher(normalizedDescription).find();
    }

    private static boolean isDemonstrativePayment(String description) {
        if (description == null || description.isBlank()) return false;
        String n = NormalizeUtil.normalize(description);
        // Conservative: only drop clear payment lines.
        if (!n.contains("pagamento") && !n.contains("pagto")) return false;
        return n.contains("pix")
                || n.contains("efetuado")
                || n.contains("debito")
                || n.contains("automatic")
                || n.startsWith("pagamento");
    }

    private static LocalDate parseIsoDateOrNull(String iso) {
        try {
            if (iso == null || iso.isBlank()) return null;
            String v = iso.trim();
            if (!v.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            return null;
        }
    }
}
