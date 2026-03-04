package com.ella.backend.services.invoices.parsers;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionType;

public class C6ExtractorParser implements InvoiceParserStrategy, PdfAwareInvoiceParser {

    private static final Logger log = LoggerFactory.getLogger(C6ExtractorParser.class);

    private final EllaExtractorClient client;
    private final C6InvoiceParser fallbackTextParser = new C6InvoiceParser();

    private static final Pattern MONEY_BRL = Pattern.compile("(?i)(?:r\\$\\s*)?([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{2})");

    public C6ExtractorParser(EllaExtractorClient client) {
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
            log.info("[C6ExtractorParser] Parsing C6 invoice via ella-extractor...");

            EllaExtractorClient.C6InvoiceResponse response = client.parseC6Invoice(pdfBytes);
            if (response == null) {
                throw new IllegalStateException("ella-extractor returned null response for C6 invoice");
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
                for (EllaExtractorClient.C6InvoiceResponse.Tx tx : response.transactions()) {
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

                    String cardName = "C6";
                    if (tx.cardFinal() != null && !tx.cardFinal().isBlank()) {
                        cardLastDigits = tx.cardFinal().trim();
                        cardName = "C6 final " + cardLastDigits;
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
                log.warn("[C6ExtractorParser] extractor returned too few txs (extractor={} fallback={}); using text fallback",
                        extractorTxCount, fallbackTxCount);
                return textFallbackPreview;
            }

            BigDecimal textInvoiceTotal = extractInvoiceTotalFromText(extractedText);
            if (textInvoiceTotal != null && (total == null || total.subtract(textInvoiceTotal).abs().compareTo(new BigDecimal("0.01")) > 0)) {
                log.warn("[C6ExtractorParser] Replacing extractor total {} with text invoice total {}", total, textInvoiceTotal);
                total = textInvoiceTotal;
            }

            return ParseResult.builder()
                    .bankName(response.bank() != null ? response.bank() : "C6")
                    .dueDate(dueDate)
                    .totalAmount(total)
                    .cardLastDigits(cardLastDigits)
                    .transactions(txs)
                    .build();
        } catch (Exception e) {
            log.warn("[C6ExtractorParser] ella-extractor failed; falling back to text parser. reason={}", e.toString());
            return parseWithFallbackText(extractedText, "extractor exception");
        }
    }

    private ParseResult parseWithFallbackText(String extractedText, String reason) {
        log.warn("[C6ExtractorParser] Falling back to text parser. reason={}", reason);
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
        return n.contains("inclusao de pagamento") || n.contains("inclusao pagamento");
    }

    private String normalizeForSearch(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private BigDecimal extractInvoiceTotalFromText(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return null;
        }

        List<BigDecimal> candidates = new ArrayList<>();
        String[] lines = extractedText.split("\\r?\\n");
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.trim();
            String normalized = normalizeForSearch(line).replaceAll("\\s+", " ").trim();
            boolean invoiceContext = normalized.contains("total a pagar")
                    || normalized.contains("valor da fatura")
                    || normalized.contains("total da fatura")
                    || normalized.contains("chegou no valor");
            if (!invoiceContext) {
                continue;
            }

            Matcher money = MONEY_BRL.matcher(line);
            while (money.find()) {
                BigDecimal amount = parseBrlMoney(money.group(1));
                if (amount != null) {
                    candidates.add(amount.abs());
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Comparator.comparingLong(java.util.Map.Entry<BigDecimal, Long>::getValue)
                        .thenComparing(e -> e.getKey(), Comparator.reverseOrder()))
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal parseBrlMoney(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(".", "").replace(",", ".");
        try {
            return new BigDecimal(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }
}
