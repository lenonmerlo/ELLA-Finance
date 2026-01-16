package com.ella.backend.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.classification.ClassificationService;
import com.ella.backend.dto.FinancialTransactionResponseDTO;
import com.ella.backend.dto.InvoiceUploadResponseDTO;
import com.ella.backend.entities.CreditCard;
import com.ella.backend.entities.FinancialTransaction;
import com.ella.backend.entities.Invoice;
import com.ella.backend.entities.User;
import com.ella.backend.enums.TransactionScope;
import com.ella.backend.enums.TransactionStatus;
import com.ella.backend.enums.TransactionType;
import com.ella.backend.repositories.CreditCardRepository;
import com.ella.backend.repositories.FinancialTransactionRepository;
import com.ella.backend.repositories.InstallmentRepository;
import com.ella.backend.repositories.InvoiceRepository;
import com.ella.backend.services.invoices.parsers.C6InvoiceParser;
import com.ella.backend.services.invoices.parsers.InvoiceParserFactory;
import com.ella.backend.services.invoices.parsers.InvoiceParserSelector;
import com.ella.backend.services.invoices.parsers.InvoiceParserStrategy;
import com.ella.backend.services.invoices.parsers.ItauInvoiceParser;
import com.ella.backend.services.invoices.parsers.TransactionData;
import com.ella.backend.services.ocr.OcrException;
import com.ella.backend.services.ocr.OcrProperties;
import com.ella.backend.services.ocr.PdfOcrExtractor;
import com.ella.backend.services.ocr.PdfTextExtractor;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadService {

    private static final org.slf4j.Logger STATIC_LOG = org.slf4j.LoggerFactory.getLogger(InvoiceUploadService.class);

    private static final String BUILD_MARKER = "2026-01-02T0826";

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;
    private final ClassificationService classificationService;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;
    private final InvoiceParserFactory invoiceParserFactory;
    private final TripDetectionService tripDetectionService;
    private final PdfTextExtractor pdfTextExtractor;
    private final PdfOcrExtractor pdfOcrExtractor;
    private final OcrProperties ocrProperties;
    private final Environment environment;

    private final AtomicBoolean debugConfigLoggedOnce = new AtomicBoolean(false);

    @Value("${ella.invoice.debug.log-extracted-text:${ella.invoice.debug.log.extracted.text:false}}")
    private boolean logExtractedText;

    @Value("${ella.invoice.debug.extracted-text-max-chars:${ella.invoice.debug.extracted.text.max.chars:2000}}")
    private int extractedTextMaxChars;

    @Value("${ella.invoice.debug.due-date-snippets:${ella.invoice.debug.due.date.snippets:false}}")
    private boolean logDueDateSnippets;

    @Value("${ella.invoice.debug.due-date-context-chars:${ella.invoice.debug.due.date.context.chars:140}}")
    private int dueDateContextChars;

    @PostConstruct
    void logDebugFlags() {
        logDebugConfigSnapshot("@PostConstruct");
    }

    private void logDebugConfigOnce(String reason) {
        if (!debugConfigLoggedOnce.compareAndSet(false, true)) return;
        logDebugConfigSnapshot(reason);
    }

    private void logDebugConfigSnapshot(String reason) {
        String envDueSnip = System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS");
        String envDueCtx = System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS");
        String envLogText = System.getenv("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT");
        String envLogMax = System.getenv("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS");
        String envProfiles = System.getenv("SPRING_PROFILES_ACTIVE");

        log.info("[InvoiceUpload] Debug snapshot reason={} activeProfiles={} SPRING_PROFILES_ACTIVE='{}'",
                reason,
                Arrays.toString(environment.getActiveProfiles()),
                envProfiles);

        log.info("[InvoiceUpload] Debug flags (injected): logExtractedText={} extractedTextMaxChars={} dueDateSnippets={} dueDateContextChars={} ",
                logExtractedText, extractedTextMaxChars, logDueDateSnippets, dueDateContextChars);

        log.info("[InvoiceUpload] Debug env vars: ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS='{}' ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS='{}' ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT='{}' ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS='{}'",
                envDueSnip, envDueCtx, envLogText, envLogMax);

        String classUrl;
        try {
            var url = InvoiceUploadService.class.getResource("InvoiceUploadService.class");
            classUrl = url == null ? "<null>" : url.toString();
        } catch (Exception e) {
            classUrl = "<error:" + e.getClass().getSimpleName() + ">";
        }
        log.info("[InvoiceUpload] Build marker={} classUrl={}", BUILD_MARKER, classUrl);

        log.info("[InvoiceUpload] Debug props (Environment): due-date-snippets='{}' due.date.snippets='{}' due-date-context-chars='{}' due.date.context.chars='{}' log-extracted-text='{}' log.extracted.text='{}' extracted-text-max-chars='{}' extracted.text.max.chars='{}'",
            environment.getProperty("ella.invoice.debug.due-date-snippets"),
            environment.getProperty("ella.invoice.debug.due.date.snippets"),
            environment.getProperty("ella.invoice.debug.due-date-context-chars"),
            environment.getProperty("ella.invoice.debug.due.date.context.chars"),
            environment.getProperty("ella.invoice.debug.log-extracted-text"),
            environment.getProperty("ella.invoice.debug.log.extracted.text"),
            environment.getProperty("ella.invoice.debug.extracted-text-max-chars"),
            environment.getProperty("ella.invoice.debug.extracted.text.max.chars"));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password, String dueDate) {
        logDebugConfigOnce("processInvoice");

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        boolean isPdf = filename.toLowerCase().endsWith(".pdf");

        try (InputStream is = file.getInputStream()) {
            List<TransactionData> transactions;
            if (isPdf) {
                ParsedPdfResult parsed = parsePdfDetailed(is, password, dueDate);
                transactions = parsed.transactions();

                // Nubank: duplicatas estritamente idênticas podem ser legítimas (ex.: duas compras iguais no mesmo dia).
                // Portanto, não removemos "exact duplicates" automaticamente para esse banco.
                boolean allowExactDuplicates = parsed.baselineParser() instanceof com.ella.backend.services.invoices.parsers.NubankInvoiceParser;
                transactions = deduplicateTransactions(transactions, allowExactDuplicates);
            } else {
                transactions = parseCsv(is);
                transactions = deduplicateTransactions(transactions);
            }

            if (transactions == null || transactions.isEmpty()) {
                if (isPdf) {
                    throw new IllegalArgumentException(
                            "Não foi possível extrair transações desse PDF. " +
                                    "Ele pode estar escaneado (imagem), protegido, ou ter um layout ainda não suportado. " +
                                    "Se o PDF for escaneado, habilite OCR (ella.ocr.enabled=true) e confirme o Tesseract/idioma instalados. " +
                                    "Como alternativa, tente exportar/enviar um CSV, ou um PDF com texto selecionável."
                    );
                }
                throw new IllegalArgumentException(
                        "Não encontrei transações neste arquivo. Confira se o CSV tem cabeçalho e colunas compatíveis."
                );
            }
            return processTransactions(transactions, filename);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process invoice file", e);
        }
    }

    private record ParsedPdfResult(List<TransactionData> transactions, InvoiceParserStrategy baselineParser) {}

    private List<TransactionData> deduplicateTransactions(List<TransactionData> transactions) {
        return deduplicateTransactions(transactions, false);
    }

    private List<TransactionData> deduplicateTransactions(List<TransactionData> transactions, boolean allowExactDuplicates) {
        if (transactions == null || transactions.isEmpty()) return transactions;

        int before = transactions.size();

        // Quando allowExactDuplicates=true, apenas removemos nulos.
        if (allowExactDuplicates) {
            List<TransactionData> cleaned = new ArrayList<>();
            int nulls = 0;
            for (TransactionData tx : transactions) {
                if (tx == null) {
                    nulls++;
                    continue;
                }
                cleaned.add(tx);
            }
            if (nulls > 0) {
                log.info("[Dedup] Dropped null transactions only ({} -> {}), droppedNulls={}", before, cleaned.size(), nulls);
            }
            return cleaned;
        }

        // Importante: não podemos deduplicar por (data+descrição+valor), pois transações legítimas podem se repetir.
        // Aqui removemos APENAS duplicatas estritamente idênticas (mesmos campos relevantes, incluindo cartão/parcelas/tipo).
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<TransactionData> uniqueTransactions = new ArrayList<>();
        int nulls = 0;
        int removed = 0;

        for (TransactionData tx : transactions) {
            if (tx == null) {
                nulls++;
                continue;
            }

            String key = exactDedupKey(tx);
            if (!seen.add(key)) {
                removed++;
                continue;
            }
            uniqueTransactions.add(tx);
        }

        int after = uniqueTransactions.size();
        if (removed > 0 || nulls > 0) {
            log.info("[Dedup] Removed {} exact duplicates ({} -> {}), droppedNulls={}", removed, before, after, nulls);
        }

        return uniqueTransactions;
    }

    private String exactDedupKey(TransactionData tx) {
        if (tx == null) return "";

        String purchaseDate = tx.date != null ? tx.date.toString() : "";
        String dueDate = tx.dueDate != null ? tx.dueDate.toString() : "";
        String amount = tx.amount != null ? tx.amount.toPlainString() : "";

        String type = tx.type != null ? tx.type.name() : "";
        String scope = tx.scope != null ? tx.scope.name() : "";

        String desc = normalizeKeyField(tx.description);
        String cardName = normalizeKeyField(tx.cardName);
        String cardholder = normalizeKeyField(tx.cardholderName);

        String instN = tx.installmentNumber != null ? tx.installmentNumber.toString() : "";
        String instT = tx.installmentTotal != null ? tx.installmentTotal.toString() : "";

        return purchaseDate + "|" + dueDate + "|" + amount + "|" + type + "|" + scope + "|" + desc + "|" + cardName + "|" + cardholder + "|" + instN + "/" + instT;
    }

    private static String normalizeKeyField(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty()) return "";
        v = v.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        return v;
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password) {
        return processInvoice(file, password, null);
    }

    private List<TransactionData> parseCsv(InputStream inputStream) throws IOException {
        List<TransactionData> transactions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            CsvFormat format = null;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isFirstLine) {
                    isFirstLine = false;
                    format = detectCsvFormat(line);
                    continue;
                }

                if (format == null) throw new IllegalArgumentException("Formato de CSV não reconhecido");

                TransactionData data = parseLine(line, format);
                if (data != null) transactions.add(data);
            }
        }
        return transactions;
    }

    private List<TransactionData> parsePdf(InputStream inputStream, String password, String dueDateOverride) throws IOException {
        return parsePdfDetailed(inputStream, password, dueDateOverride).transactions();
    }

    private ParsedPdfResult parsePdfDetailed(InputStream inputStream, String password, String dueDateOverride) throws IOException {
        forceDebugHeader("parsePdf");
        logDebugConfigOnce("parsePdf");

        LocalDate dueDateFromRequest = null;
        if (dueDateOverride != null && !dueDateOverride.isBlank()) {
            dueDateFromRequest = parseDueDateOverrideOrThrow(dueDateOverride);
        }

        try (PDDocument document = (password != null && !password.isBlank())
                ? PDDocument.load(inputStream, password)
                : PDDocument.load(inputStream)) {
            try {
                document.setAllSecurityToBeRemoved(true);
            } catch (Exception ignored) {
            }

            String text = pdfTextExtractor.extractText(document);
            text = text == null ? "" : text;

            // Identifica o parser baseado no texto do PDFBox para decidir políticas específicas.
            InvoiceParserStrategy baselineParser = null;
            try {
                InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(invoiceParserFactory.getParsers(), text);
                baselineParser = selection != null && selection.chosen() != null ? selection.chosen().parser() : null;
            } catch (Exception ignored) {
                baselineParser = null;
            }
                boolean skipOcrForItauOrC6OrNubank = baselineParser instanceof ItauInvoiceParser
                    || baselineParser instanceof C6InvoiceParser
                    || baselineParser instanceof com.ella.backend.services.invoices.parsers.NubankInvoiceParser;

            logExtractedTextIfEnabled("PDFBox", text);
            logDueDateSignalsIfEnabled("PDFBox", text);

            boolean ocrAttempted = false;
            if (shouldAttemptOcr(text)) {
                if (skipOcrForItauOrC6OrNubank) {
                    log.info("[InvoiceUpload][OCR] Skipping OCR for Itau/C6/Nubank (disabled for these parsers)");
                } else {
                    text = runOcrOrThrow(document);
                    ocrAttempted = true;

                    logExtractedTextIfEnabled("OCR", text);
                    logDueDateSignalsIfEnabled("OCR", text);
                }
            }

            if (text.isBlank()) {
                return new ParsedPdfResult(List.of(), baselineParser);
            }

            String classUrl;
            try {
                var url = InvoiceUploadService.class.getResource("InvoiceUploadService.class");
                classUrl = url == null ? "<null>" : url.toString();
            } catch (Exception e) {
                classUrl = "<error:" + e.getClass().getSimpleName() + ">";
            }

            log.info("[InvoiceUpload][BUILD_MARKER={}] PDF extracted sample (classUrl={}): {}",
                    BUILD_MARKER,
                    classUrl,
                    (text.length() > 500 ? text.substring(0, 500) : text));

            try {
                List<TransactionData> transactions = parsePdfText(text, dueDateFromRequest);

                // Diagnostic: if the invoice total is present in the extracted text, log the comparison once.
                // This makes it obvious whether the OCR retry is being skipped due to threshold logic,
                // missing expected total extraction, or an early OCR attempt.
                if (transactions != null && !transactions.isEmpty()) {
                    BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
                    if (expectedTotal != null && expectedTotal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal extractedTotal = sumExpenseAmounts(transactions);
                        BigDecimal pct = extractedTotal.compareTo(BigDecimal.ZERO) > 0
                                ? extractedTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(expectedTotal, 2, java.math.RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        STATIC_LOG.info(
                                "[InvoiceUpload][OCR] Pre-check: ocrAttempted={} txCount={} extracted={} expected={} ({}% of expected)",
                                ocrAttempted, transactions.size(), extractedTotal, expectedTotal, pct);
                    }
                }

                if ((transactions == null || transactions.isEmpty()) && !ocrAttempted && ocrProperties.isEnabled()) {
                    if (skipOcrForItauOrC6OrNubank) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR empty-result retry for Itau/C6/Nubank (disabled for these parsers)");
                    } else {
                        String ocrText = runOcrOrThrow(document);
                        transactions = parsePdfText(ocrText, dueDateFromRequest);
                        ocrAttempted = true;
                    }
                }

                // Some PDFs contain selectable text but with broken font encoding, producing "garbled" merchants.
                // If we detect low-quality descriptions (or many missing purchase dates), prefer OCR when enabled.
                if (transactions != null && !transactions.isEmpty() && !ocrAttempted && ocrProperties.isEnabled()
                        && shouldRetryWithOcrForQuality(transactions)) {
                    if (skipOcrForItauOrC6OrNubank) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR quality retry for Itau/C6/Nubank (disabled for these parsers)");
                    } else {
                        log.info("[OCR] Trigger: parsed transactions look garbled; retrying once with OCR...");
                        String ocrText = runOcrOrThrow(document);
                        ocrAttempted = true;
                        List<TransactionData> ocrTransactions = parsePdfText(ocrText, dueDateFromRequest);
                        if (isOcrResultBetter(ocrTransactions, transactions)) {
                            logInvoiceTotalValidation("OCR", ocrText, ocrTransactions);
                            return new ParsedPdfResult(ocrTransactions, baselineParser);
                        }
                    }
                }

                // Detect missing transactions by comparing extracted total vs. the invoice total shown on the PDF.
                // This is intentionally conservative to avoid triggering OCR for other banks/layouts.
                if (transactions != null && !transactions.isEmpty() && !ocrAttempted && ocrProperties.isEnabled()
                        && shouldRetryDueToMissingTransactions(transactions, text)) {
                    if (skipOcrForItauOrC6OrNubank) {
                        log.info("[InvoiceUpload][OCR] Skipping OCR missing-transactions retry for Itau/C6/Nubank (disabled for these parsers)");

                        // Non-OCR fallback: retry PDFBox extraction with positional sorting.
                        try {
                            String sortedText = pdfTextExtractor.extractTextSorted(document);
                            if (sortedText != null && !sortedText.isBlank()) {
                                List<TransactionData> sortedTransactions = parsePdfText(sortedText, dueDateFromRequest);

                                BigDecimal expected = extractInvoiceExpectedTotal(text);
                                if (expected == null) {
                                    expected = extractInvoiceExpectedTotal(sortedText);
                                }

                                if (expected != null
                                        && isOcrResultBetterForMissingTransactions(sortedTransactions, transactions, expected)) {
                                    logInvoiceTotalValidation("PDFBox-sorted", sortedText, sortedTransactions);
                                        return new ParsedPdfResult(sortedTransactions, baselineParser);
                                }
                            }
                        } catch (Exception e) {
                            log.info("[InvoiceUpload][PDFBox] Sorted extraction retry failed: {}", e.getMessage());
                        }
                    } else {
                        log.info("[OCR] Trigger: possible missing transactions (total mismatch); retrying once with OCR...");
                        String ocrText = runOcrOrThrow(document);
                        ocrAttempted = true;
                        List<TransactionData> ocrTransactions = parsePdfText(ocrText, dueDateFromRequest);

                        BigDecimal expected = extractInvoiceExpectedTotal(text);
                        if (expected == null) {
                            expected = extractInvoiceExpectedTotal(ocrText);
                        }

                        if (expected != null && isOcrResultBetterForMissingTransactions(ocrTransactions, transactions, expected)) {
                            logInvoiceTotalValidation("OCR", ocrText, ocrTransactions);
                            return new ParsedPdfResult(ocrTransactions, baselineParser);
                        }
                    }
                }

                logInvoiceTotalValidation(ocrAttempted ? "OCR" : "PDFBox", text, transactions);
                return new ParsedPdfResult(transactions, baselineParser);
            } catch (IllegalArgumentException e) {
                // If parsing fails (missing due date / unsupported layout / etc), retry once with OCR when enabled.
                // Rationale: many PDFs have selectable text but the due date (or table) is rendered as an image.
                if (!ocrAttempted && ocrProperties.isEnabled()) {
                    if (skipOcrForItauOrC6OrNubank) {
                        log.warn("[InvoiceUpload][OCR] Skipping OCR exception retry for Itau/C6/Nubank (disabled for these parsers): {}", e.getMessage());
                        throw e;
                    }
                    log.warn("[OCR] Parsing failed ({}). Retrying once with OCR...", e.getMessage());
                    ocrAttempted = true;
                    String ocrText = runOcrOrThrow(document);
                    logDueDateSignalsIfEnabled("OCR-retry", ocrText);
                    List<TransactionData> parsed = parsePdfText(ocrText, dueDateFromRequest);
                    logInvoiceTotalValidation("OCR", ocrText, parsed);
                    return new ParsedPdfResult(parsed, baselineParser);
                }
                throw e;
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
    }

    private void logInvoiceTotalValidation(String source, String text, List<TransactionData> transactions) {
        try {
            if (transactions == null || transactions.isEmpty()) return;

            BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
            if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal extractedTotal = sumExpenseAmounts(transactions);
            BigDecimal difference = extractedTotal.subtract(expectedTotal).abs();

            log.info("[VALIDATION][{}] Total Extraído: R$ {}, Total Esperado: R$ {}, Diferença: R$ {}",
                    source, extractedTotal, expectedTotal, difference);

            if (difference.compareTo(new BigDecimal("0.01")) > 0) {
                log.warn("[VALIDATION][{}] A diferença entre o total extraído e o esperado é maior que 1 centavo!", source);
            }
        } catch (Exception ignored) {
        }
    }

    static boolean shouldRetryWithOcrForQuality(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return false;

        int total = 0;
        int garbled = 0;
        int missingDate = 0;

        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }

        if (total == 0) return false;

        // Trigger only when there's a clear signal to avoid doing OCR unnecessarily.
        // For "garbled" merchants (typically broken font encoding), a single strong signal is enough.
        boolean anyGarbledDescs = garbled >= 1;
        boolean manyMissingDates = missingDate >= Math.max(2, (int) Math.ceil(total * 0.5));
        boolean trigger = manyMissingDates || anyGarbledDescs;

        if (trigger) {
            // Log a small sample so we can confirm in prod why OCR was triggered.
            StringBuilder sample = new StringBuilder();
            int shown = 0;
            for (TransactionData tx : transactions) {
                if (tx == null) continue;
                if (!isLikelyGarbledMerchant(tx.description) && tx.date != null) continue;
                if (shown >= 3) break;
                String desc = tx.description == null ? "" : tx.description;
                if (desc.length() > 60) desc = desc.substring(0, 60) + "...";
                sample.append('[').append(tx.date).append(']').append(desc).append(' ');
                shown++;
            }
            STATIC_LOG.info("[OCR] Quality trigger: total={} garbled={} missingDate={} samples={}", total, garbled, missingDate, sample.toString().trim());
        }

        return trigger;
    }

    private static final BigDecimal MISSING_TX_RATIO_THRESHOLD = new BigDecimal("0.96");

    static boolean shouldRetryDueToMissingTransactions(List<TransactionData> transactions, String text) {
        if (transactions == null || transactions.isEmpty()) return false;

        BigDecimal expectedTotal = extractInvoiceExpectedTotal(text);
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            // No reliable "total da fatura" found; don't trigger OCR based on totals.
            return false;
        }

        BigDecimal totalExtracted = sumExpenseAmounts(transactions);
        if (totalExtracted.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal threshold = expectedTotal.multiply(MISSING_TX_RATIO_THRESHOLD);
        boolean trigger = totalExtracted.compareTo(threshold) < 0;

        // Always log the comparison when we have a reliable expected total.
        // This helps diagnose cases where OCR was expected to trigger but didn't.
        BigDecimal pct = totalExtracted.multiply(BigDecimal.valueOf(100))
            .divide(expectedTotal, 2, java.math.RoundingMode.HALF_UP);
        STATIC_LOG.info(
            "[InvoiceUpload][OCR] Total check: txCount={} extracted={} expected={} threshold={} ({}% of expected) trigger={}",
            transactions.size(), totalExtracted, expectedTotal, threshold, pct, trigger);

        return trigger;
    }

    private static boolean isOcrResultBetterForMissingTransactions(
            List<TransactionData> ocrTransactions,
            List<TransactionData> originalTransactions,
            BigDecimal expectedTotal
    ) {
        if (expectedTotal == null || expectedTotal.compareTo(BigDecimal.ZERO) <= 0) return false;

        BigDecimal origTotal = sumExpenseAmounts(originalTransactions);
        BigDecimal ocrTotal = sumExpenseAmounts(ocrTransactions);

        BigDecimal origDiff = expectedTotal.subtract(origTotal).abs();
        BigDecimal ocrDiff = expectedTotal.subtract(ocrTotal).abs();

        int origCount = originalTransactions == null ? 0 : originalTransactions.size();
        int ocrCount = ocrTransactions == null ? 0 : ocrTransactions.size();

        boolean better = false;

        // Prefer OCR if it brings total closer to expected and doesn't reduce the number of transactions.
        if (ocrCount >= origCount && ocrDiff.compareTo(origDiff) < 0) {
            better = true;
        }

        // Or if OCR increases tx count and doesn't make the total worse.
        if (!better && ocrCount > origCount && ocrDiff.compareTo(origDiff) <= 0) {
            better = true;
        }

        STATIC_LOG.info("[InvoiceUpload][OCR] OCR retry evaluated: txCount {} -> {} | extracted {} -> {} | diffToExpected {} -> {} | accepted={}",
                origCount, ocrCount, origTotal, ocrTotal, origDiff, ocrDiff, better);

        return better;
    }

    private static BigDecimal sumAbsoluteAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null) continue;
            sum = sum.add(tx.amount.abs());
        }
        return sum;
    }

    private static BigDecimal sumExpenseAmounts(List<TransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (TransactionData tx : transactions) {
            if (tx == null || tx.amount == null) continue;
            if (tx.type != null && tx.type != TransactionType.EXPENSE) continue;
            sum = sum.add(tx.amount.abs());
        }
        return sum;
    }

    private static BigDecimal extractInvoiceExpectedTotal(String text) {
        if (text == null || text.isBlank()) return null;

        String normalized = text.replace('\u00A0', ' ');

        // Keep patterns specific to avoid false positives across banks.
        List<Pattern> patterns = List.of(
                Pattern.compile("(?is)\\btotal\\s+(?:da\\s+)?fatura\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
                Pattern.compile("(?is)\\btotal\\s+a\\s+pagar\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])"),
                Pattern.compile("(?is)\\bvalor\\s+total\\b[^0-9]{0,25}R?\\$?\\s*([0-9][0-9\\s\\.,]{0,25}[0-9])")
        );

        for (Pattern p : patterns) {
            Matcher m = p.matcher(normalized);
            if (!m.find()) continue;
            BigDecimal v = parseBrlAmountLoose(m.group(1));
            if (v != null && v.compareTo(BigDecimal.ZERO) > 0) {
                return v;
            }
        }

        return null;
    }

    private static BigDecimal parseBrlAmountLoose(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace("R$", "").replace(" ", "");
        // Keep only digits, dot and comma.
        s = s.replaceAll("[^0-9,\\.]", "");
        if (s.isEmpty()) return null;

        // Brazilian style: 1.234,56
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            // If not clearly a decimal format, treat dots as thousand separators.
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

    static boolean isOcrResultBetter(List<TransactionData> ocrResult, List<TransactionData> original) {
        int ocrScore = qualityScore(ocrResult);
        int origScore = qualityScore(original);
        return ocrScore > origScore;
    }

    private static int qualityScore(List<TransactionData> txs) {
        if (txs == null || txs.isEmpty()) return 0;
        int total = 0;
        int garbled = 0;
        int missingDate = 0;
        for (TransactionData tx : txs) {
            if (tx == null) continue;
            total++;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) garbled++;
        }
        if (total == 0) return 0;

        // Prefer more transactions, fewer garbled descriptions, and fewer missing dates.
        int score = total * 1000;
        score -= garbled * 250;
        score -= missingDate * 250;
        return score;
    }

    static boolean isLikelyGarbledMerchant(String description) {
        if (description == null) return false;
        String d = description.trim();
        if (d.isEmpty()) return false;

        // Ignore common short/expected tokens.
        String upper = d.toUpperCase(java.util.Locale.ROOT);
        if (upper.contains("UBER") || upper.contains("IFOOD") || upper.contains("PAGAMENTO") || upper.contains("ANUIDADE")) {
            return false;
        }

        int letters = 0;
        int digits = 0;
        int vowels = 0;
        int longAlnumTokens = 0;
        int mixedLetterDigitTokens = 0;

        for (String token : d.split("\\s+")) {
            String t = token.replaceAll("[^A-Za-z0-9]", "");
            if (t.length() < 8) continue;

            boolean hasLetter = t.matches(".*[A-Za-z].*");
            boolean hasDigit = t.matches(".*\\d.*");
            if (t.length() >= 10 && t.matches("[A-Za-z0-9]+")) {
                longAlnumTokens++;
            }
            if (t.length() >= 10 && hasLetter && hasDigit && countDigits(t) >= 2) {
                mixedLetterDigitTokens++;
            }
        }

        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                letters++;
                if (c == 'A' || c == 'E' || c == 'I' || c == 'O' || c == 'U') vowels++;
            } else if (c >= '0' && c <= '9') {
                digits++;
            }
        }

        // Common Mercado Pago garbling: tokens like "bPU3OTOSY6GLEy" (long, mixed letters+digits).
        if (mixedLetterDigitTokens >= 1) {
            return true;
        }

        // Fallback: lots of alnum, long tokens, very few vowels.
        if (longAlnumTokens >= 1 && letters >= 8 && digits >= 2 && vowels <= 1) {
            return true;
        }
        if (letters >= 12 && vowels == 0 && longAlnumTokens >= 2) {
            return true;
        }

        return false;
    }

    private static int countDigits(String value) {
        if (value == null || value.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9') count++;
        }
        return count;
    }

    private void forceDebugHeader(String where) {
        try {
            System.err.println("=== INVOICE UPLOAD DEBUG HEADER ===");
            System.err.println("where: " + where);
            System.err.println("buildMarker: " + BUILD_MARKER);
            System.err.println("activeProfiles: " + Arrays.toString(environment.getActiveProfiles()));
            System.err.println("SPRING_PROFILES_ACTIVE: " + System.getenv("SPRING_PROFILES_ACTIVE"));
            System.err.println("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS: " + System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_SNIPPETS"));
            System.err.println("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS: " + System.getenv("ELLA_INVOICE_DEBUG_DUE_DATE_CONTEXT_CHARS"));
            System.err.println("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT: " + System.getenv("ELLA_INVOICE_DEBUG_LOG_EXTRACTED_TEXT"));
            System.err.println("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS: " + System.getenv("ELLA_INVOICE_DEBUG_EXTRACTED_TEXT_MAX_CHARS"));

            System.err.println("prop ella.invoice.debug.due-date-snippets: " + environment.getProperty("ella.invoice.debug.due-date-snippets"));
            System.err.println("prop ella.invoice.debug.due.date.snippets: " + environment.getProperty("ella.invoice.debug.due.date.snippets"));
            System.err.println("prop ella.invoice.debug.due-date-context-chars: " + environment.getProperty("ella.invoice.debug.due-date-context-chars"));
            System.err.println("prop ella.invoice.debug.due.date.context.chars: " + environment.getProperty("ella.invoice.debug.due.date.context.chars"));

            System.err.println("injected dueDateSnippets: " + logDueDateSnippets);
            System.err.println("injected dueDateContextChars: " + dueDateContextChars);
            System.err.println("injected logExtractedText: " + logExtractedText);
            System.err.println("injected extractedTextMaxChars: " + extractedTextMaxChars);
            System.err.println("=== /INVOICE UPLOAD DEBUG HEADER ===");
        } catch (Exception ignored) {
            // If anything goes wrong, don't break uploads.
        }
    }

    private void logExtractedTextIfEnabled(String source, String text) {
        if (!logExtractedText) return;
        String t = text == null ? "" : text;

        String payload = t;
        int max = extractedTextMaxChars;
        if (max > 0 && t.length() > max) {
            payload = t.substring(0, max) + "\n... (truncated)";
        }

        log.info("[InvoiceUpload][DEBUG] Extracted text source={} len={} maxChars={}\n--BEGIN--\n{}\n--END--",
                source,
                t.length(),
                max,
                payload
        );
    }

    private void logDueDateSignalsIfEnabled(String source, String text) {
        if (!logDueDateSnippets) return;

        String t = text == null ? "" : text;
        String normalized = normalizeNumericDates(t);

        boolean hasKeyword = Pattern.compile("(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b").matcher(normalized).find();
        boolean hasDdMmYyyy = Pattern.compile("(?s)\\b\\d{2}/\\d{2}/\\d{4}\\b").matcher(normalized).find();
        boolean hasDdMm = Pattern.compile("(?s)\\b\\d{2}/\\d{2}\\b").matcher(normalized).find();

        log.info("[InvoiceUpload][DEBUG] DueDate signals source={} len={} hasKeyword={} hasDdMmYyyy={} hasDdMm={}",
                source, normalized.length(), hasKeyword, hasDdMmYyyy, hasDdMm);

        if (!hasKeyword) return;

        Matcher m = Pattern.compile("(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b").matcher(normalized);
        if (!m.find()) return;

        int start = Math.max(0, m.start() - Math.max(0, dueDateContextChars));
        int end = Math.min(normalized.length(), m.end() + Math.max(0, dueDateContextChars));
        String snippet = normalized.substring(start, end);
        log.info("[InvoiceUpload][DEBUG] DueDate keyword snippet source={}\n--SNIP--\n{}\n--/SNIP--", source, snippet);
    }

    private boolean shouldAttemptOcr(String extractedText) {
        if (!ocrProperties.isEnabled()) return false;

        String t = extractedText == null ? "" : extractedText;
        if (t.isBlank()) {
            log.info("[OCR] Trigger: extracted text is blank");
            return true;
        }

        int minLen = Math.max(0, ocrProperties.getPdf().getMinTextLength());

        int nonWhitespace = 0;
        int alnum = 0;
        int replacement = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!Character.isWhitespace(c)) nonWhitespace++;
            if (Character.isLetterOrDigit(c)) alnum++;
            if (c == '\uFFFD') replacement++;
        }

        // Heuristic: some scanned PDFs yield mostly whitespace or garbled extraction.
        // Use non-whitespace/alnum counts rather than raw length.
        boolean tooLittleSignal = nonWhitespace < minLen || alnum < Math.max(40, minLen / 2);
        boolean tooGarbled = replacement > 10;

        if (tooLittleSignal || tooGarbled) {
            log.info("[OCR] Trigger: enabled=true textLen={} nonWs={} alnum={} minTextLen={} replacement={}",
                    t.length(), nonWhitespace, alnum, minLen, replacement);
            return true;
        }

        return false;
    }

    private String runOcrOrThrow(PDDocument document) {
        try {
            log.info("[OCR] Attempting OCR fallback (enabled=true)");
            String ocrText = pdfOcrExtractor.extractText(document);
            return ocrText == null ? "" : ocrText;
        } catch (OcrException e) {
            throw new IllegalArgumentException(
                    "Falha ao aplicar OCR neste PDF. " +
                            "Verifique se o Tesseract está instalado e se a configuração está correta " +
                            "(ella.ocr.enabled, ella.ocr.language, ella.ocr.tessdata-path).",
                    e
            );
        }
    }

    private List<TransactionData> parsePdfText(String text, LocalDate dueDateFromRequest) {
        String t = text == null ? "" : text;

        InvoiceParserSelector.Selection selection = InvoiceParserSelector.selectBest(invoiceParserFactory.getParsers(), t);
        InvoiceParserSelector.Candidate chosen = selection.chosen();
        InvoiceParserStrategy parser = chosen.parser();

        LocalDate dueDate = chosen.dueDate();
        if (dueDate == null) {
            log.warn("[InvoiceUpload] Parser={} matched but dueDate was not found by parser. Trying fallback extractor...",
                    parser.getClass().getSimpleName());
            dueDate = tryExtractDueDateFallback(t);
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

        List<TransactionData> transactions = chosen.transactions() != null ? chosen.transactions() : parser.extractTransactions(t);
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            tx.setDueDate(dueDate);
        }

        // Itaú PDFs sometimes have the "próximas faturas" section extracted out-of-order by PDFBox.
        // As a defensive measure, drop EXPENSE rows that are clearly after the due date.
        if (parser instanceof ItauInvoiceParser && dueDate != null && transactions != null && !transactions.isEmpty()) {
            int before = transactions.size();
            int dropped = 0;
            List<TransactionData> filtered = new ArrayList<>(transactions.size());
            for (TransactionData tx : transactions) {
                if (tx == null) {
                    filtered.add(null);
                    continue;
                }
                if (tx.type == TransactionType.EXPENSE && tx.date != null && tx.date.isAfter(dueDate)) {
                    dropped++;
                    continue;
                }
                filtered.add(tx);
            }
            if (dropped > 0) {
                log.info("[InvoiceUpload][Itau] Dropped {} EXPENSE rows after dueDate={} ({} -> {})", dropped, dueDate, before, before - dropped);
            }
            transactions = filtered;
        }

        int garbled = 0;
        int missingDate = 0;
        List<String> sample = new ArrayList<>();
        for (TransactionData tx : transactions) {
            if (tx == null) continue;
            if (tx.date == null) missingDate++;
            if (isLikelyGarbledMerchant(tx.description)) {
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
        return transactions;
    }

    private LocalDate parseDueDateOverrideOrThrow(String dueDate) {
        String v = dueDate == null ? "" : dueDate.trim();
        if (v.isEmpty()) return null;

        // Accept ISO (yyyy-MM-dd) or Brazilian (dd/MM/yyyy).
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (Exception ignored) {
            }
        }

        throw new IllegalArgumentException(
                "Formato inválido para dueDate. Use yyyy-MM-dd (ex: 2025-12-20) ou dd/MM/yyyy (ex: 20/12/2025)."
        );
    }

    private LocalDate tryExtractDueDateFallback(String text) {
        if (text == null || text.isBlank()) return null;

        String normalizedText = normalizeNumericDates(text);

        Integer inferredYear = inferYearFromText(normalizedText);

        // 1) Formatos numéricos com palavra-chave de vencimento próxima.
        // Ex.: "Vencimento 20/12/2025", "VENCIMENTO: 20.12.2025", "Data de vencimento: 12/12/2025"
        var p1 = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^\\d]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})\\s*[\\./-]\\s*(\\d{4})");
        var m1 = p1.matcher(normalizedText);
        if (m1.find()) {
            Integer day = parseIntOrNull(m1.group(2));
            Integer month = parseIntOrNull(m1.group(3));
            Integer year = parseIntOrNull(m1.group(4));
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via numeric pattern: {}", d);
                return d;
            }
        }

        // 1b) Sem ano: "Vencimento 20/12" (inferimos o ano do próprio documento quando possível)
        var p1b = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^\\d]{0,30}(\\d{2})\\s*[\\./-]\\s*(\\d{2})(?!\\s*[\\./-]\\s*\\d{4})");
        var m1b = p1b.matcher(normalizedText);
        if (m1b.find()) {
            Integer day = parseIntOrNull(m1b.group(2));
            Integer month = parseIntOrNull(m1b.group(3));
            Integer year = inferredYear != null ? inferredYear : LocalDate.now().getYear();
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via numeric pattern (no year): {}", d);
                return d;
            }
        }

        // 2) Formato Nubank/Santander textual: "... vencimento: 12 DEZ 2025" ou "FATURA 12 DEZ 2025"
        var p2 = java.util.regex.Pattern.compile(
            "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento|fatura)\\b[^\\d]{0,30}(\\d{2})\\s+([A-Z]{3})\\s+(\\d{4})");
        var m2 = p2.matcher(normalizedText);
        if (m2.find()) {
            Integer day = parseIntOrNull(m2.group(2));
            Integer month = monthAbbrevToNumber(m2.group(3));
            Integer year = parseIntOrNull(m2.group(4));
            LocalDate d = safeDate(year, month, day);
            if (d != null) {
                log.warn("[InvoiceUpload] Fallback dueDate extracted via textual pattern: {}", d);
                return d;
            }
        }

        // 3) Ultra-flexível: captura dígitos perto de 'vencimento/vcto' mesmo quando PDFBox separa cada dígito.
        // Ex: "Vencimento: 2 2 1 2 2 0 2 5" ou "Vencimento 22 12 2025".
        try {
            var m3 = java.util.regex.Pattern.compile(
                    "(?is)\\b(venc(?:imento)?|vct(?:o)?|data\\s+de\\s+vencimento)\\b[^0-9]{0,60}([0-9][0-9\\s\\./-]{5,30})")
                    .matcher(text.replace('\u00A0', ' '));
            if (m3.find()) {
                String digits = m3.group(2).replaceAll("\\D", "");
                if (digits.length() >= 8) {
                    Integer day = parseIntOrNull(digits.substring(0, 2));
                    Integer month = parseIntOrNull(digits.substring(2, 4));
                    Integer year = parseIntOrNull(digits.substring(4, 8));
                    LocalDate d = safeDate(year, month, day);
                    if (d != null) {
                        log.warn("[InvoiceUpload] Fallback dueDate extracted via digits-only pattern: {}", d);
                        return d;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private Integer inferYearFromText(String text) {
        try {
            if (text == null || text.isBlank()) return null;
            var m = java.util.regex.Pattern.compile("(?s)\\b\\d{2}\\s*/\\s*\\d{2}\\s*/\\s*(\\d{4})\\b").matcher(text);
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
        t = t.replaceAll("(?<=\\d)\\s+(?=\\d)", "");
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

    private Integer monthAbbrevToNumber(String mon) {
        if (mon == null) return null;
        String m = mon.trim().toUpperCase(java.util.Locale.ROOT);
        if (m.isEmpty()) return null;
        return switch (m) {
            case "JAN" -> 1;
            case "FEV" -> 2;
            case "MAR" -> 3;
            case "ABR" -> 4;
            case "MAI" -> 5;
            case "JUN" -> 6;
            case "JUL" -> 7;
            case "AGO" -> 8;
            case "SET" -> 9;
            case "OUT" -> 10;
            case "NOV" -> 11;
            case "DEZ" -> 12;
            default -> null;
        };
    }

    private InvoiceUploadResponseDTO processTransactions(List<TransactionData> transactions, String originalFilename) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalStateException("Usuário não autenticado");
        User user = userService.findByEmail(auth.getName());

        List<FinancialTransactionResponseDTO> responseTransactions = new ArrayList<>();
        List<FinancialTransaction> savedTransactions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Invoice lastInvoice = null;

        Map<String, CreditCard> cardCache = new HashMap<>();

        for (TransactionData data : transactions) {
            CardMetadata cardMetadata = extractCardMetadata(data.cardName, originalFilename);
            String cacheKey = (cardMetadata.brand() + "|" + (cardMetadata.lastFourDigits() != null
                    ? cardMetadata.lastFourDigits()
                    : cardMetadata.name())).toLowerCase();

            CreditCard card = cardCache.computeIfAbsent(cacheKey, key -> resolveOrCreateCard(user, cardMetadata, data.cardholderName));
            if (card == null) continue;

            LocalDate invoiceDueDate = data.dueDate;
            if (invoiceDueDate == null) {
                invoiceDueDate = estimateDueDateFromCardAndTxDate(card, data.date);
            }
            if (invoiceDueDate == null) {
                invoiceDueDate = data.date;
            }

            Invoice invoice = getOrCreateInvoice(card, invoiceDueDate);
            lastInvoice = invoice;

            FinancialTransaction tx = saveTransaction(user, data, invoiceDueDate);
            savedTransactions.add(tx);
            createOrLinkInstallment(invoice, tx, data);

            if (data.type == TransactionType.EXPENSE) {
                invoice.setTotalAmount(invoice.getTotalAmount().add(data.amount));
            }
            invoiceRepository.save(invoice);

            responseTransactions.add(mapToDTO(tx));
            totalAmount = totalAmount.add(tx.getAmount());
        }

        LocalDate startDate = transactions.stream().map(t -> t.date).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate endDate = transactions.stream().map(t -> t.date).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);

        var tripSuggestion = tripDetectionService.detect(savedTransactions).orElse(null);

        return InvoiceUploadResponseDTO.builder()
                .invoiceId(lastInvoice != null ? lastInvoice.getId() : null)
                .totalAmount(totalAmount)
                .totalTransactions(responseTransactions.size())
                .startDate(startDate)
                .endDate(endDate)
                .transactions(responseTransactions)
            .tripSuggestion(tripSuggestion)
                .build();
    }

    private LocalDate estimateDueDateFromCardAndTxDate(CreditCard card, LocalDate txDate) {
        if (txDate == null) return null;

        Integer dueDay = null;
        try {
            dueDay = card != null ? card.getDueDay() : null;
        } catch (Exception ignored) {
        }

        int safeDueDay = (dueDay != null && dueDay >= 1 && dueDay <= 28) ? dueDay : 10;
        LocalDate base = LocalDate.of(txDate.getYear(), txDate.getMonthValue(), 1);
        LocalDate nextMonth = base.plusMonths(1);
        int dom = Math.min(safeDueDay, nextMonth.lengthOfMonth());
        return nextMonth.withDayOfMonth(dom);
    }

    private FinancialTransaction saveTransaction(User user, TransactionData txData, LocalDate invoiceDueDate) {
        LocalDate resolvedDueDate = txData.dueDate != null ? txData.dueDate : invoiceDueDate;
        if (resolvedDueDate == null) {
            resolvedDueDate = txData.date;
        }

        String resolvedCategory = txData.category;
        if (isUncategorizedCategory(resolvedCategory)) {
            var suggestion = classificationService.suggest(user.getId(), txData.description, txData.amount, txData.type);
            resolvedCategory = suggestion.category();
        }

        FinancialTransaction entity = FinancialTransaction.builder()
                .person(user)
                .description(txData.description)
                .amount(txData.amount)
                .type(txData.type)
                .scope(txData.scope != null ? txData.scope : TransactionScope.PERSONAL)
                .category(resolvedCategory)
                // Regra: referência (mês do dashboard) = vencimento da fatura
                .transactionDate(resolvedDueDate)
                // Preserva a data original da compra/linha
                .purchaseDate(txData.date)
                .dueDate(resolvedDueDate)
                .status(TransactionStatus.PENDING)
                .build();
        return Objects.requireNonNull(transactionRepository.save(entity));
    }

    private boolean isUncategorizedCategory(String category) {
        if (category == null) return true;
        String c = category.trim();
        if (c.isEmpty()) return true;
        return "Outros".equalsIgnoreCase(c) || "Other".equalsIgnoreCase(c);
    }

    private FinancialTransactionResponseDTO mapToDTO(FinancialTransaction tx) {
        return new FinancialTransactionResponseDTO(
            tx.getId().toString(),
            tx.getPerson().getId().toString(),
            tx.getPerson().getName(),
            tx.getDescription(),
            tx.getAmount(),
            tx.getType(),
            tx.getScope(),
            tx.getCategory(),
            tx.getTripId() != null ? tx.getTripId().toString() : null, // Added tripId
            tx.getTripSubcategory(), // Added tripSubcategory
            tx.getTransactionDate(),
            tx.getPurchaseDate(),
            tx.getDueDate(),
            tx.getPaidDate(),
            tx.getStatus(),
            tx.getCreatedAt(),
            tx.getUpdatedAt()
        );
    }

    private CreditCard resolveOrCreateCard(User user, CardMetadata cardMetadata, String parsedCardholderName) {
        // Prefer busca direta (userId + last4 + banco)
        try {
            if (user != null && user.getId() != null
                    && cardMetadata != null
                    && cardMetadata.lastFourDigits() != null
                    && cardMetadata.brand() != null) {
                var found = creditCardRepository.findByUserIdAndLastFourDigitsAndBankName(
                        user.getId(),
                        cardMetadata.lastFourDigits(),
                        cardMetadata.brand()
                );
                if (found.isPresent()) {
                    CreditCard c = found.get();
                    maybeUpdateCardholderName(c, parsedCardholderName, cardMetadata);
                    return c;
                }
            }
        } catch (Exception ignored) {}

        try {
            var cards = creditCardRepository.findByOwner(user);
            if (cards != null) {
                // Prioriza match por últimos 4 dígitos + bandeira
                if (cardMetadata.lastFourDigits() != null) {
                    for (var c : cards) {
                        if (c.getLastFourDigits() != null
                                && c.getLastFourDigits().equals(cardMetadata.lastFourDigits())
                                && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                            maybeUpdateCardholderName(c, parsedCardholderName, cardMetadata);
                            return c;
                        }
                    }
                }

                // Fallback: nome + bandeira
                for (var c : cards) {
                    if (equalsIgnoreCase(c.getName(), cardMetadata.name())
                            && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                        maybeUpdateCardholderName(c, parsedCardholderName, cardMetadata);
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {}

        CreditCard cc = new CreditCard();
        cc.setOwner(user);
        cc.setName(cardMetadata.name());
        cc.setCardholderName(resolveCardholderName(parsedCardholderName, user));
        cc.setBrand(cardMetadata.brand());
        cc.setLastFourDigits(cardMetadata.lastFourDigits());
        cc.setLimitAmount(java.math.BigDecimal.valueOf(10000));
        cc.setClosingDay(5);
        cc.setDueDay(15);
        return creditCardRepository.save(cc);
    }

    private String resolveCardholderName(String parsedCardholderName, User user) {
        if (parsedCardholderName != null && !parsedCardholderName.isBlank()) {
            return parsedCardholderName.trim();
        }
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName().trim();
        }
        return "Titular";
    }

    private void maybeUpdateCardholderName(CreditCard card, String parsedCardholderName, CardMetadata meta) {
        if (card == null) return;
        if (parsedCardholderName == null || parsedCardholderName.isBlank()) return;

        String current = card.getCardholderName();
        if (current != null && !current.isBlank() && equalsIgnoreCase(current, parsedCardholderName)) {
            return;
        }

        // Se o valor atual parece "genérico" (banco/nome do cartão), substitui pelo titular real
        boolean looksGeneric = (current == null || current.isBlank());
        if (!looksGeneric && meta != null) {
            looksGeneric = equalsIgnoreCase(current, meta.brand()) || equalsIgnoreCase(current, meta.name());
        }
        if (!looksGeneric) {
            looksGeneric = equalsIgnoreCase(current, card.getBrand()) || equalsIgnoreCase(current, card.getName());
        }

        if (looksGeneric) {
            card.setCardholderName(parsedCardholderName.trim());
            try {
                creditCardRepository.save(card);
            } catch (Exception ignored) {}
        }
    }

    private CardMetadata extractCardMetadata(String candidateName, String filename) {
        String metaSource = candidateName != null ? candidateName : "";
        String brand = detectBrand(metaSource);
        if (brand == null) {
            brand = detectBrand(filename);
        }

        String lastFour = detectLastFour(metaSource);
        if (lastFour == null) {
            lastFour = detectLastFour(filename);
        }

        String name = (candidateName != null && !candidateName.isBlank()) ? candidateName.trim() : null;
        if (name == null) {
            name = brand != null ? brand : (filename != null && !filename.isBlank() ? filename : "Cartão Padrão");
        }
        if (brand == null) {
            brand = name;
        }

        return new CardMetadata(name, brand, lastFour);
    }

    private String detectBrand(String source) {
        if (source == null) return null;
        String s = source.toLowerCase();
        if (s.contains("visa")) return "Visa";
        if (s.contains("mastercard") || s.contains("master")) return "Mastercard";
        if (s.contains("elo")) return "Elo";
        if (s.contains("amex") || s.contains("american express")) return "Amex";
        if (s.contains("hipercard")) return "Hipercard";
        if (s.contains("diners")) return "Diners";
        if (s.contains("nubank")) return "Nubank";
        if (s.contains("santander")) return "Santander";
        if (s.contains("itau")) return "Itaú";
        if (s.contains("bradesco")) return "Bradesco";
        if (s.contains("c6")) return "C6 Bank";
        if (s.contains("inter")) return "Banco Inter";
        return null;
    }

    private String detectLastFour(String source) {
        if (source == null) return null;
        Pattern lastFourPattern = Pattern.compile("(?:\\u002A{4}|\\*)?\\s*(\\d{4})(?!.*\\d)");
        Matcher m = lastFourPattern.matcher(source);
        if (m.find()) {
            return m.group(1);
        }

        Pattern finalPattern = Pattern.compile("final\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
        Matcher m2 = finalPattern.matcher(source);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
    }

    private Invoice getOrCreateInvoice(CreditCard card, LocalDate invoiceDueDate) {
        final LocalDate resolvedInvoiceDueDate = (invoiceDueDate != null) ? invoiceDueDate : LocalDate.now();

        int month = resolvedInvoiceDueDate.getMonthValue();
        int year = resolvedInvoiceDueDate.getYear();
        return invoiceRepository
                .findByCardAndMonthAndYear(card, month, year)
                .orElseGet(() -> {
                    Invoice inv = new Invoice();
                    inv.setCard(card);
                    inv.setMonth(month);
                    inv.setYear(year);
                    inv.setDueDate(resolvedInvoiceDueDate);
                    inv.setTotalAmount(BigDecimal.ZERO);
                    inv.setPaidAmount(BigDecimal.ZERO);
                    return invoiceRepository.save(inv);
                });
    }

    private void createOrLinkInstallment(Invoice invoice, FinancialTransaction entity, TransactionData txData) {
        int installmentNumber = txData.installmentNumber != null ? txData.installmentNumber : 1;
        int installmentTotal = txData.installmentTotal != null ? txData.installmentTotal : 1;
        LocalDate installmentDueDate = txData.dueDate != null ? txData.dueDate : invoice.getDueDate();
        if (installmentDueDate == null) {
            installmentDueDate = txData.date;
        }

        try {
            var existing = installmentRepository.findByTransaction(entity);
            if (existing != null && !existing.isEmpty()) {
                var inst = existing.get(0);
                boolean changed = false;
                if (!invoice.equals(inst.getInvoice())) {
                    inst.setInvoice(invoice);
                    changed = true;
                }
                if (inst.getNumber() == null || !inst.getNumber().equals(installmentNumber)) {
                    inst.setNumber(installmentNumber);
                    changed = true;
                }
                if (inst.getTotal() == null || !inst.getTotal().equals(installmentTotal)) {
                    inst.setTotal(installmentTotal);
                    changed = true;
                }
                if (inst.getAmount() == null || inst.getAmount().compareTo(txData.amount) != 0) {
                    inst.setAmount(txData.amount);
                    changed = true;
                }
                if (inst.getDueDate() == null || !inst.getDueDate().equals(installmentDueDate)) {
                    inst.setDueDate(installmentDueDate);
                    changed = true;
                }

                if (changed) {
                    installmentRepository.save(inst);
                }
                return;
            }
        } catch (Exception ignored) {}

        var installment = new com.ella.backend.entities.Installment();
        installment.setInvoice(invoice);
        installment.setTransaction(entity);
        installment.setNumber(installmentNumber);
        installment.setTotal(installmentTotal);
        installment.setAmount(txData.amount);
        installment.setDueDate(installmentDueDate);
        installmentRepository.save(installment);
    }

    private CsvFormat detectCsvFormat(String header) {
        String h = header.toLowerCase();
        if (h.contains("nome") && h.contains("cpf") && h.contains("cartão") && h.contains("data")) {
            return CsvFormat.FULL_EXPORT;
        }
        if (h.contains("data") && h.contains("descrição") && h.contains("valor")) {
            return CsvFormat.PORTUGUESE;
        }
        if (h.contains("description") && h.contains("amount") && h.contains("date")) {
            return CsvFormat.ENGLISH;
        }
        return CsvFormat.UNKNOWN;
    }

    private TransactionData parseLine(String line, CsvFormat format) {
        try {
            String[] fields = line.split(",");

            if (format == CsvFormat.FULL_EXPORT) {
                if (fields.length < 6) return null;
                String cardName = fields[2].trim();
                LocalDate date = parseDate(fields[3].trim());
                String description = fields[4].trim();
                BigDecimal amount = new BigDecimal(fields[5].trim());
                String category = (fields.length > 6) ? fields[6].trim() : "Outros";
                // Inverted logic: Positive amount is EXPENSE (Purchase), Negative is INCOME (Payment/Credit)
                TransactionType type = amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
                category = normalizeCategory(category);
                TransactionScope scope = inferScope(description, cardName);
                InstallmentInfo installment = extractInstallmentInfo(description);
                TransactionData data = new TransactionData(description, amount.abs(), type, category, date, cardName, scope);
                applyInstallmentInfo(data, installment);
                return data;

            } else if (format == CsvFormat.PORTUGUESE) {
                if (fields.length < 4) return null;
                LocalDate date = parseDate(fields[0].trim());
                String description = fields[1].trim();
                BigDecimal amount = new BigDecimal(fields[2].trim());
                String category = fields[3].trim();
                // Inverted logic: Positive amount is EXPENSE (Purchase), Negative is INCOME (Payment/Credit)
                TransactionType type = amount.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
                category = normalizeCategory(category);
                TransactionScope scope = inferScope(description, null);
                InstallmentInfo installment = extractInstallmentInfo(description);
                TransactionData data = new TransactionData(description, amount.abs(), type, category, date, null, scope);
                applyInstallmentInfo(data, installment);
                return data;

            } else if (format == CsvFormat.ENGLISH) {
                if (fields.length < 4) return null;
                String description = fields[0].trim();
                BigDecimal amount = new BigDecimal(fields[1].trim()).abs();
                String category = fields[2].trim();
                LocalDate date = parseDate(fields[3].trim());
                TransactionType type = TransactionType.EXPENSE;
                if (fields.length > 4) {
                    String typeStr = fields[4].trim().toUpperCase();
                    if ("INCOME".equals(typeStr)) {
                        type = TransactionType.INCOME;
                    }
                }
                if (category == null || category.isEmpty()) {
                    category = categorizeDescription(description, type);
                }
                TransactionScope scope = inferScope(description, null);
                InstallmentInfo installment = extractInstallmentInfo(description);
                TransactionData data = new TransactionData(description, amount, type, category, date, null, scope);
                applyInstallmentInfo(data, installment);
                return data;
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CSV] Erro ao parsear linha: " + line + " - " + e.getMessage());
            return null;
        }
    }

    private void applyInstallmentInfo(TransactionData data, InstallmentInfo installment) {
        if (data == null || installment == null) return;
        data.installmentNumber = installment.number();
        data.installmentTotal = installment.total();
    }

    private InstallmentInfo extractInstallmentInfo(String description) {
        if (description == null || description.isBlank()) return null;
        String normalized = description.toLowerCase();

        Pattern slashPattern = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,2})");
        Matcher slashMatcher = slashPattern.matcher(normalized);
        if (slashMatcher.find()) {
            int number = Integer.parseInt(slashMatcher.group(1));
            int total = Integer.parseInt(slashMatcher.group(2));
            if (number > 0 && total > 0) {
                return new InstallmentInfo(number, total);
            }
        }

        Pattern timesPattern = Pattern.compile("(\\d{1,2})\\s*x\\s*(\\d{1,2})");
        Matcher timesMatcher = timesPattern.matcher(normalized);
        if (timesMatcher.find()) {
            int number = Integer.parseInt(timesMatcher.group(1));
            int total = Integer.parseInt(timesMatcher.group(2));
            if (number > 0 && total > 0) {
                return new InstallmentInfo(number, total);
            }
        }

        return null;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            // Tenta formato dd MMM (ex: 12 DEZ)
            if (dateStr.matches("\\d{2}\\s+[A-Za-z]{3}")) {
                String[] parts = dateStr.split("\\s+");
                int day = Integer.parseInt(parts[0]);
                String monthStr = parts[1].toUpperCase();
                int month = switch (monthStr) {
                    case "JAN" -> 1;
                    case "FEV", "FEB" -> 2;
                    case "MAR" -> 3;
                    case "ABR", "APR" -> 4;
                    case "MAI", "MAY" -> 5;
                    case "JUN" -> 6;
                    case "JUL" -> 7;
                    case "AGO", "AUG" -> 8;
                    case "SET", "SEP" -> 9;
                    case "OUT", "OCT" -> 10;
                    case "NOV" -> 11;
                    case "DEZ", "DEC" -> 12;
                    default -> LocalDate.now().getMonthValue();
                };
                
                int year = LocalDate.now().getYear();
                LocalDate parsed = LocalDate.of(year, month, day);
                
                // Se a data for futura em relação a hoje + 1 mês, assume ano anterior
                if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                    return parsed.minusYears(1);
                }
                return parsed;
            }

            return LocalDate.parse(dateStr);
        } catch (Exception e1) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dateStr, formatter);
            } catch (Exception e2) {
                try {
                    // Tenta formato curto dd/MM assumindo ano atual
                    // Se a data for futura em relação a hoje, assume ano anterior (ex: upload em Jan/25 de fatura de Dez/24)
                    LocalDate parsed = LocalDate.parse(dateStr, new java.time.format.DateTimeFormatterBuilder()
                        .appendPattern("dd/MM")
                        .parseDefaulting(java.time.temporal.ChronoField.YEAR, LocalDate.now().getYear())
                        .toFormatter());
                    
                    if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                        return parsed.minusYears(1);
                    }
                    return parsed;
                } catch (Exception e3) {
                    return LocalDate.now();
                }
            }
        }
    }

    private String normalizeCategory(String category) {
        if (category == null) return "Outros";
        String c = category.trim().toLowerCase();

        // Normaliza entradas comuns (pt/en) para os rótulos usados no app
        if (c.contains("transporte") || c.contains("transport")) return "Transporte";
        if (c.contains("alimentação") || c.contains("alimentacao") || c.contains("food")) return "Alimentação";
        if (c.contains("mercado") || c.contains("supermerc") || c.contains("grocer")) return "Mercado";
        if (c.contains("stream") || c.contains("streaming")) return "Streaming";
        if (c.contains("lazer") || c.contains("entretenimento") || c.contains("entertain")) return "Lazer";
        if (c.contains("saúde") || c.contains("saude") || c.contains("health") || c.contains("medic")) return "Saúde";
        if (c.contains("farm") || c.contains("drog") || c.contains("farmácia") || c.contains("farmacia")) return "Farmácia";
        if (c.contains("internet")) return "Internet";
        if (c.contains("celular") || c.contains("telefone")) return "Celular";
        if (c.contains("aluguel") || c.contains("rent")) return "Aluguel";
        if (c.contains("moradia") || c.contains("housing") || c.contains("casa")) return "Moradia";

        // Fallback
        if ("other".equals(c) || "outros".equals(c)) return "Outros";
        return category;
    }

    private String categorizeDescription(String description, TransactionType type) {
        // Categoria pode ser preenchida depois em saveTransaction(...) via ClassificationService
        // quando estiver vazia/"Outros".
        return "Outros";
    }

    private TransactionScope inferScope(String description, String cardName) {
        String d = description == null ? "" : description.toLowerCase();
        String c = cardName == null ? "" : cardName.toLowerCase();

        if (d.contains("cnpj") || d.contains("mei") || d.contains("ltda") || d.contains("eireli")
                || d.contains("pj") || c.contains("cnpj") || c.contains("pj") || c.contains("empresa")) {
            return TransactionScope.BUSINESS;
        }

        if (d.contains("fornecedor") || d.contains("insumo") || d.contains("estoque") || d.contains("maquininha")) {
            return TransactionScope.BUSINESS;
        }

        return TransactionScope.PERSONAL;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }

    private enum CsvFormat {
        PORTUGUESE, ENGLISH, FULL_EXPORT, UNKNOWN
    }

    private record InstallmentInfo(Integer number, Integer total) {}

    private record CardMetadata(String name, String brand, String lastFourDigits) {}
}
