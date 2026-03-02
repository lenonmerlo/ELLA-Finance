package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.util.NormalizeUtil;

/**
 * Parser específico para o novo layout Bradesco "Fatura Mensal" (v1).
 *
 * - Seleção por heurística em texto extraído (PDFBox/OCR)
 * - Parsing principal via PDF (ella-extractor /parse/bradesco-fatura-mensal-v1)
 */
public class BradescoFaturaMensalV1InvoiceParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final String CARD_NAME = "Bradesco";
    private static final String DEFAULT_CATEGORY = "Outros";

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EllaExtractorBradescoFaturaMensalV1Client client;
    private final BradescoInvoiceParser fallbackTextParser = new BradescoInvoiceParser();

    public BradescoFaturaMensalV1InvoiceParser() {
        this(new EllaExtractorBradescoFaturaMensalV1Client());
    }

    BradescoFaturaMensalV1InvoiceParser(EllaExtractorBradescoFaturaMensalV1Client client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;

        String n = NormalizeUtil.normalize(text);

        boolean hasBradescoMarker = n.contains("bradesco")
                || n.contains("banco bradesco")
                || n.contains("bradesco s/a")
                || n.contains("bradesco sa");

        boolean hasFaturaMensal = n.contains("fatura mensal");

        boolean hasLayoutSignals = n.contains("total da fatura")
                || n.contains("total da fatura em real")
                || n.contains("lancamentos")
                || n.contains("vencimento");

        boolean hasItauMarker = n.contains("itau")
                || n.contains("banco itau")
                || n.contains("itau unibanco")
                || n.contains("unibanco")
                || n.contains("itaucard")
                || n.contains("itau card")
                || n.contains("itau cares")
                || n.contains("itacares")
                || n.contains("itaucares");

        boolean hasSantanderMarker = n.contains("santander");
        boolean hasNubankMarker = n.contains("nubank");
        boolean hasBbMarker = n.contains("banco do brasil") || n.contains("bb");
        boolean hasC6Marker = n.contains("c6");
        boolean hasSicrediMarker = n.contains("sicredi");

        if (hasItauMarker || hasSantanderMarker || hasNubankMarker || hasBbMarker || hasC6Marker || hasSicrediMarker) {
            return false;
        }

        return hasBradescoMarker && hasFaturaMensal && hasLayoutSignals;
    }

    @Override
    public LocalDate extractDueDate(String text) {
        // Reuse robust Bradesco due-date extraction for selector scoring and fallback.
        return fallbackTextParser.extractDueDate(text);
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        // Keep selector scoring aligned with the generic Bradesco parser.
        // The pipeline will call parseWithPdf for the real parsing.
        List<TransactionData> txs = fallbackTextParser.extractTransactions(text);
        if (txs == null || txs.isEmpty()) return List.of();

        for (TransactionData tx : txs) {
            if (tx == null) continue;
            tx.category = DEFAULT_CATEGORY;
            tx.cardName = CARD_NAME;
            tx.scope = TransactionScope.PERSONAL;
        }
        return txs;
    }

    @Override
    public ParseResult parseWithPdf(byte[] pdfBytes, String extractedText) {
        EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response resp = client.parseBradescoFaturaMensalV1(pdfBytes);
        if (resp == null) {
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
        List<String> unmatched = new ArrayList<>();
        int droppedEmpty = 0;
        int droppedNoise = 0;
        int droppedPayment = 0;
        int droppedAmount = 0;
        int droppedDate = 0;
        int droppedAboveTotal = 0;
        if (resp.transactions() != null) {
            for (EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.Tx tx : resp.transactions()) {
                if (tx == null) continue;

                String desc = normalizeSpaces(tx.description());
                if (desc == null || desc.isBlank()) {
                    droppedEmpty++;
                    continue;
                }

                String nd = NormalizeUtil.normalize(desc);
                if (shouldSkipNoiseLine(nd)) {
                    droppedNoise++;
                    unmatched.add(desc + " [backend-filter:noise]");
                    continue;
                }
                if (isDemonstrativePayment(nd)) {
                    // Payments are demonstrative (should not become transactions).
                    droppedPayment++;
                    unmatched.add(desc + " [backend-filter:payment]");
                    continue;
                }

                BigDecimal amount;
                try {
                    amount = tx.amount() == null ? null : BigDecimal.valueOf(tx.amount());
                } catch (Exception ignored) {
                    amount = null;
                }
                if (amount == null) {
                    droppedAmount++;
                    unmatched.add(desc + " [backend-filter:invalid-amount]");
                    continue;
                }

                amount = amount.abs();
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    droppedAmount++;
                    unmatched.add(desc + " [backend-filter:non-positive-amount]");
                    continue;
                }

                TransactionType type = isRefundOrCreditTransaction(nd)
                        ? TransactionType.INCOME
                        : TransactionType.EXPENSE;

                String category = MerchantCategoryMapper.categorize(desc, type);

                // Sanity: a single EXPENSE line can't exceed the invoice total.
                if (type == TransactionType.EXPENSE && totalAmount != null && amount.compareTo(totalAmount) > 0) {
                    droppedAboveTotal++;
                    unmatched.add(desc + " [backend-filter:amount-above-invoice-total]");
                    continue;
                }

                LocalDate txDate = parseIsoDateOrNull(tx.date());
                if (txDate == null) {
                    droppedDate++;
                    unmatched.add(desc + " [backend-filter:missing-date]");
                    continue;
                }

                TransactionData td = new TransactionData(
                        desc,
                        amount,
                        type,
                    category,
                        txDate,
                        CARD_NAME,
                        TransactionScope.PERSONAL
                );

                Integer instCurrent = tx.installmentCurrent();
                Integer instTotal = tx.installmentTotal();
                if ((instCurrent == null || instTotal == null) && tx.installment() != null) {
                    instCurrent = tx.installment().current();
                    instTotal = tx.installment().total();
                }
                if (instCurrent != null && instTotal != null && instCurrent > 0 && instTotal > 0) {
                    td.installmentNumber = instCurrent;
                    td.installmentTotal = instTotal;
                }

                out.add(td);
            }
        }

        System.out.println("[BradescoV1][backend-filter] in=" + (resp.transactions() != null ? resp.transactions().size() : 0)
                + " kept=" + out.size()
                + " droppedEmpty=" + droppedEmpty
                + " droppedNoise=" + droppedNoise
                + " droppedPayment=" + droppedPayment
                + " droppedAmount=" + droppedAmount
                + " droppedDate=" + droppedDate
                + " droppedAboveTotal=" + droppedAboveTotal);

        if (resp.unmatchedTransactions() != null) {
            for (EllaExtractorBradescoFaturaMensalV1Client.BradescoFaturaMensalV1Response.UnmatchedTx ut : resp.unmatchedTransactions()) {
                if (ut == null) continue;
                String line = normalizeSpaces(ut.line());
                if (line == null || line.isBlank()) continue;
                String reason = normalizeSpaces(ut.reason());
                if (reason != null && !reason.isBlank()) {
                    unmatched.add(line + " [" + reason + "]");
                } else {
                    unmatched.add(line);
                }
            }
        }

        return ParseResult.builder()
                .dueDate(dueDate)
                .totalAmount(totalAmount)
                .transactions(out)
                .unmatchedTransactions(unmatched)
                .bankName(CARD_NAME)
                .build();
    }

    private static boolean isDemonstrativePayment(String normalizedDescription) {
        if (normalizedDescription == null || normalizedDescription.isBlank()) return false;
        String nd = normalizedDescription;

        // Common Bradesco payment markers.
        if (nd.contains("pagto") || nd.contains("pagamento")) return true;
        if (nd.contains("deb em c/c") || nd.contains("debito em conta") || nd.contains("debito em c/c")) return true;

        return false;
    }

    private static boolean isRefundOrCreditTransaction(String normalizedDescription) {
        if (normalizedDescription == null || normalizedDescription.isBlank()) return false;
        String nd = normalizedDescription;

        // Strong refund markers.
        if (nd.contains("estorno") || nd.contains("devolucao") || nd.contains("reembolso")) {
            return true;
        }

        // "crédito" is ambiguous in this invoice layout because legal/table text can leak into
        // valid merchant lines (e.g. "crédito rotativo", "cartão de crédito"). Keep it conservative.
        boolean hasCreditWord = nd.contains("credito") || nd.contains("creditos");
        if (!hasCreditWord) {
            return false;
        }

        if (nd.contains("credito rotativo")
                || nd.contains("cartao de credito")
                || nd.contains("limite maximo de juros")
                || nd.contains("juros e encargos")) {
            return false;
        }

        // Treat as income only when credit wording is clearly transactional.
        return nd.startsWith("credito ")
                || nd.startsWith("credito:")
                || nd.contains(" credito de ")
                || nd.contains(" estorno de credito ");
    }

    private static boolean shouldSkipNoiseLine(String normalizedDescription) {
        if (normalizedDescription == null || normalizedDescription.isBlank()) return true;
        String nd = normalizedDescription;

        // Keep this conservative: only drop clear section headers at the beginning of the line.
        if (startsWithAny(nd,
                "central de atendimento",
                "mensagem importante",
                "resumo da fatura",
                "fique atento",
                "taxas mensais",
                "novo teto de juros",
                "programa de fidelidade",
                "pontos acumulados",
                "saldo de pontos",
                "total parcelados",
                "total para as proximas faturas")) return true;

        // Limits table sometimes leaks into OCR/PDFBox text and can be mistakenly parsed as a merchant.
        if (looksLikeLimitsTableLine(nd)) return true;

        return false;
    }

    private static boolean startsWithAny(String text, String... prefixes) {
        if (text == null || text.isBlank() || prefixes == null) return false;
        for (String p : prefixes) {
            if (p == null || p.isBlank()) continue;
            if (text.startsWith(p)) return true;
        }
        return false;
    }

    private static boolean looksLikeLimitsTableLine(String nd) {
        // Heuristic: multiple currency markers + keywords typical of the limits table.
        int currencyCount = countOccurrences(nd, "r$");
        if (currencyCount >= 2 && (nd.contains("limites") || nd.contains("disponivel") || nd.contains("utilizado") || nd.contains("compras") || nd.contains("saque"))) {
            return true;
        }

        // Another common artifact: "Saque R$ 15.000,00 R$ 0,00" glued into a line.
        if (nd.contains("saque") && currencyCount >= 2 && nd.contains("r$ 0")) {
            return true;
        }

        return false;
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while (true) {
            idx = haystack.indexOf(needle, idx);
            if (idx < 0) return count;
            count++;
            idx += needle.length();
        }
    }

    private static LocalDate parseIsoDateOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return LocalDate.parse(s.trim(), ISO);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeSpaces(String s) {
        if (s == null) return null;
        return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }
}
