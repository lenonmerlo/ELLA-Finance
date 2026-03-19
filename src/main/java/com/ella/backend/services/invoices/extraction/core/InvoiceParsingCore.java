package com.ella.backend.services.invoices.extraction.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import com.ella.backend.services.invoices.InvoiceParsingException;
import com.ella.backend.services.invoices.extraction.ParserParent;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.parsers.InvoiceParserSelector;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import com.ella.backend.services.invoices.parsers.ParseResult;
import com.ella.backend.services.invoices.parsers.PdfAwareInvoiceParser;
import com.ella.backend.services.invoices.parsers.SantanderExtractorParser;
import com.ella.backend.services.invoices.parsers.SantanderInvoiceParser;
import com.ella.backend.services.invoices.parsers.TransactionData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceParsingCore implements ParserParent {

    private static final String UNSUPPORTED_MERCADO_PAGO_MESSAGE =
            "Ainda não suportamos faturas do Mercado Pago. Envie a fatura de outro banco/cartão ou tente novamente mais tarde.";

    private final InvoiceParserFactory invoiceParserFactory;

    @Override
    public ParseResult parse(
            byte[] pdfBytes,
            String text,
            LocalDate dueDateFromRequest,
            Function<String, LocalDate> dueDateFallbackExtractor,
            Predicate<String> unsupportedInvoiceDetector
    ) {
        String normalizedText = text == null ? "" : text;

        if (unsupportedInvoiceDetector.test(normalizedText)) {
            throw new InvoiceParsingException(UNSUPPORTED_MERCADO_PAGO_MESSAGE);
        }

        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(invoiceParserFactory.getParsers(), normalizedText);
        InvoiceParserSelector.Candidate chosen = selection.chosen();
        InvoiceParserStrategy parser = chosen.parser();

        LocalDate dueDate = chosen.dueDate();
        List<TransactionData> transactions = chosen.transactions();

        if (parser instanceof PdfAwareInvoiceParser pdfAware) {
            try {
                ParseResult pdfParse = pdfAware.parseWithPdf(pdfBytes, normalizedText);
                if (pdfParse != null && pdfParse.getDueDate() != null) {
                    dueDate = pdfParse.getDueDate();
                }
                if (pdfParse != null && pdfParse.getTransactions() != null && !pdfParse.getTransactions().isEmpty()) {
                    transactions = pdfParse.getTransactions();
                }
            } catch (Exception e) {
                log.warn("[InvoiceUpload] Pdf-aware parse failed, falling back to text parser. reason={}", e.toString());
            }
        }

        if (dueDate == null) {
            log.warn("[InvoiceUpload] Parser={} matched but dueDate was not found by parser. Trying fallback extractor...",
                    parser.getClass().getSimpleName());
            dueDate = dueDateFallbackExtractor.apply(normalizedText);
        }

        if (dueDate == null && dueDateFromRequest != null) {
            log.warn("[InvoiceUpload] Using dueDate override from request: {}", dueDateFromRequest);
            dueDate = dueDateFromRequest;
        }

        if (dueDate == null) {
            throw new IllegalArgumentException(
                    "Não foi possível determinar a data de vencimento da fatura. " +
                            "O processamento foi interrompido para evitar lançamentos incorretos. " +
                            "(parser=" + parser.getClass().getSimpleName() + ")"
            );
        }

        transactions = transactions != null ? transactions : parser.extractTransactions(normalizedText);
        TransactionNormalizer.NormalizationResult normalization = TransactionNormalizer.normalize(parser, dueDate, transactions);
        transactions = normalization.transactions();
        if (normalization.droppedAfterDueDate() > 0) {
            log.info("[InvoiceUpload][Itau] Dropped {} EXPENSE rows after dueDate={} ({} -> {})",
                    normalization.droppedAfterDueDate(),
                    dueDate,
                    normalization.beforeCount(),
                    normalization.beforeCount() - normalization.droppedAfterDueDate());
        }

        int garbled = 0;
        int missingDate = 0;
        List<String> sample = new ArrayList<>();
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            if (tx.date == null) missingDate++;
            if (ReconciliationPolicy.isLikelyGarbledMerchant(tx.description)) {
                garbled++;
                if (sample.size() < 3 && tx.description != null) {
                    String s = tx.description.trim();
                    sample.add(s.length() > 60 ? s.substring(0, 60) : s);
                }
            }
        }

        log.info("[InvoiceUpload] Using parser={} dueDate={} txCount={}",
                parser.getClass().getSimpleName(), dueDate, transactions.size());
        log.info("[InvoiceUpload] Parse quality: txCount={} garbled={} missingDate={} garbledSamples={}",
                transactions.size(), garbled, missingDate, sample);

        BigDecimal totalAmount = TotalResolver.extractInvoiceExpectedTotal(normalizedText);
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (parser instanceof SantanderInvoiceParser || parser instanceof SantanderExtractorParser) {
                totalAmount = sumNetAmounts(transactions);
            } else {
                totalAmount = sumExpenseAmounts(transactions);
            }
        }

        return ParseResult.builder()
                .transactions(transactions)
                .dueDate(dueDate)
                .totalAmount(totalAmount)
                .bankName(parser.getClass().getSimpleName())
                .build();
    }

    private static BigDecimal sumExpenseAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null) continue;
            if (tx.type != null && tx.type != com.ella.backend.enums.TransactionType.EXPENSE) continue;
            sum = sum.add(tx.amount.abs());
        }
        return sum;
    }

    private static BigDecimal sumNetAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null || tx.type == null) continue;

            if (tx.type == com.ella.backend.enums.TransactionType.EXPENSE) {
                sum = sum.add(tx.amount.abs());
            } else if (tx.type == com.ella.backend.enums.TransactionType.INCOME) {
                sum = sum.subtract(tx.amount.abs());
            }
        }
        return sum;
    }
}
