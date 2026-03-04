package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class BancoDoBrasilExtractorParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(BancoDoBrasilExtractorParser.class);

    private final EllaExtractorClient client;
    private final BancoDoBrasilInvoiceParser fallbackTextParser = new BancoDoBrasilInvoiceParser();

    public BancoDoBrasilExtractorParser(EllaExtractorClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public boolean isApplicable(String text) {
        return fallbackTextParser.isApplicable(text);
    }

    @Override
    public LocalDate extractDueDate(String text) {
        return fallbackTextParser.extractDueDate(text);
    }

    @Override
    public List<TransactionData> extractTransactions(String text) {
        return fallbackTextParser.extractTransactions(text);
    }

    @Override
    public ParseResult parseWithPdf(byte[] pdfBytes, String extractedText) {
        try {
            log.info("[BancoDoBrasilExtractorParser] Parsing BB via ella-extractor...");

            EllaExtractorClient.BancoDoBrasilResponse response = client.parseBancoDoBrasil(pdfBytes);
            if (response == null) {
                throw new IllegalStateException("ella-extractor returned null response for Banco do Brasil");
            }

            ParseResult textFallbackPreview = parseTextFallback(extractedText);

            LocalDate dueDate = null;
            if (response.dueDate() != null && !response.dueDate().isBlank()) {
                try {
                    dueDate = LocalDate.parse(response.dueDate().trim());
                } catch (Exception ignored) {
                }
            }

            if (dueDate == null) {
                return parseWithFallbackText(extractedText, "extractor dueDate missing/invalid");
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
                for (EllaExtractorClient.BancoDoBrasilResponse.Tx tx : response.transactions()) {
                    if (tx == null) continue;
                    if (tx.description() == null || tx.description().isBlank()) continue;
                    if (tx.amount() == null) continue;
                    if (tx.date() == null || tx.date().isBlank()) continue;

                    String description = tx.description().trim();
                    if (isPaymentFromPreviousInvoice(description)) {
                        continue;
                    }

                    LocalDate purchaseDate;
                    try {
                        purchaseDate = LocalDate.parse(tx.date().trim());
                    } catch (Exception ignored) {
                        continue;
                    }

                    BigDecimal signedAmount = BigDecimal.valueOf(tx.amount());
                    TransactionType type = signedAmount.signum() < 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
                    BigDecimal amount = signedAmount.abs();

                    String category = MerchantCategoryMapper.categorize(description, type);

                    String cardName = "Banco do Brasil";
                    if (tx.cardFinal() != null && !tx.cardFinal().isBlank()) {
                        cardLastDigits = tx.cardFinal().trim();
                        cardName = "Banco do Brasil final " + cardLastDigits;
                    }

                    TransactionData td = new TransactionData(
                            description,
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

            int extractorTxCount = txs.size();
            int fallbackTxCount = textFallbackPreview.getTransactions() == null ? 0 : textFallbackPreview.getTransactions().size();
            if (shouldPreferTextFallback(extractorTxCount, fallbackTxCount)) {
                log.warn("[BancoDoBrasilExtractorParser] extractor returned too few txs (extractor={} fallback={}); using text fallback",
                        extractorTxCount, fallbackTxCount);
                return textFallbackPreview;
            }

            return ParseResult.builder()
                    .bankName(response.bank() != null ? response.bank() : "BANCO_DO_BRASIL")
                    .dueDate(dueDate)
                    .totalAmount(total)
                    .cardLastDigits(cardLastDigits)
                    .transactions(txs)
                    .build();
        } catch (Exception e) {
            log.warn("[BancoDoBrasilExtractorParser] ella-extractor failed; falling back to text parser. reason={}", e.toString());
            return parseWithFallbackText(extractedText, "extractor exception");
        }
    }

    private ParseResult parseWithFallbackText(String extractedText, String reason) {
        log.warn("[BancoDoBrasilExtractorParser] Falling back to text parser. reason={}", reason);
        return parseTextFallback(extractedText);
    }

    private ParseResult parseTextFallback(String extractedText) {
        LocalDate dueDate = extractDueDate(extractedText);
        List<TransactionData> txs = extractTransactions(extractedText);
        return ParseResult.builder()
                .transactions(txs)
                .dueDate(dueDate)
                .build();
    }

    private boolean shouldPreferTextFallback(int extractorTxCount, int fallbackTxCount) {
        if (extractorTxCount <= 0 && fallbackTxCount > 0) return true;
        if (fallbackTxCount < 10) return false;
        return extractorTxCount < Math.ceil(fallbackTxCount * 0.70);
    }

    private boolean isPaymentFromPreviousInvoice(String description) {
        if (description == null || description.isBlank()) return false;

        String n = normalizeForSearch(description)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return n.startsWith("pgto cobranca")
                || n.startsWith("pagto cobranca")
                || n.startsWith("pagamento cobranca");
    }

    private String normalizeForSearch(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }
}
