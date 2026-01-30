package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.services.invoices.util.NormalizeUtil;

public class SicrediExtractorParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(SicrediExtractorParser.class);

    private final EllaExtractorClient client;
    private final SicrediInvoiceParser fallbackTextParser = new SicrediInvoiceParser();

    public SicrediExtractorParser(EllaExtractorClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public boolean isApplicable(String text) {
        if (text == null || text.isBlank()) return false;
        String n = NormalizeUtil.normalize(text);
        return n.contains("sicredi") && n.contains("resumo da fatura");
    }

    @Override
    public LocalDate extractDueDate(String text) {
        return fallbackTextParser.extractDueDate(text);
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        // Keep a text-based fallback for resilience, but prefer PDF-aware parsing.
        return fallbackTextParser.extractTransactions(text);
    }

    @Override
    public ParseResult parseWithPdf(byte[] pdfBytes, String extractedText) {
        log.info("[SicrediExtractorParser] Parsing Sicredi via ella-extractor...");

        EllaExtractorClient.SicrediResponse response = client.parseSicredi(pdfBytes);
        if (response == null) {
            throw new IllegalStateException("ella-extractor returned null response for Sicredi");
        }

        LocalDate dueDate = null;
        if (response.dueDate() != null && !response.dueDate().isBlank()) {
            try {
                dueDate = LocalDate.parse(response.dueDate().trim());
            } catch (Exception ignored) {
            }
        }

        BigDecimal total = null;
        if (response.total() != null) {
            try {
                total = BigDecimal.valueOf(response.total());
            } catch (Exception ignored) {
            }
        }

        List<TransactionData> txs = new ArrayList<>();
        String cardLastDigits = null;

        if (response.transactions() != null) {
            for (EllaExtractorClient.SicrediResponse.Tx tx : response.transactions()) {
                if (tx == null) continue;
                if (tx.description() == null || tx.description().isBlank()) continue;
                if (tx.amount() == null) continue;
                if (tx.date() == null || tx.date().isBlank()) continue;

                LocalDate purchaseDate;
                try {
                    purchaseDate = LocalDate.parse(tx.date().trim());
                } catch (Exception ignored) {
                    continue;
                }

                BigDecimal signedAmount = BigDecimal.valueOf(tx.amount());
                TransactionType type = signedAmount.signum() < 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
                BigDecimal amount = signedAmount.abs();

                String category = MerchantCategoryMapper.categorize(tx.description(), type);

                String cardName = "Sicredi";
                if (tx.cardFinal() != null && !tx.cardFinal().isBlank()) {
                    cardLastDigits = tx.cardFinal().trim();
                    cardName = "Sicredi final " + cardLastDigits;
                }

                TransactionData td = new TransactionData(
                        tx.description().trim(),
                        amount,
                        type,
                        category,
                        purchaseDate,
                        cardName,
                        TransactionScope.PERSONAL
                );

                if (tx.installment() != null) {
                    td.installmentNumber = tx.installment().current();
                    td.installmentTotal = tx.installment().total();
                }

                txs.add(td);
            }
        }

        return ParseResult.builder()
                .bankName(response.bank() != null ? response.bank() : "SICREDI")
                .dueDate(dueDate)
                .totalAmount(total)
                .cardLastDigits(cardLastDigits)
                .transactions(txs)
                .build();
    }
}
