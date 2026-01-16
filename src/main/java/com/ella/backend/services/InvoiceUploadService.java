package com.ella.backend.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cache.annotation.CacheEvict;
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
import com.ella.backend.services.invoices.extraction.ExtractionPipeline;
import com.ella.backend.services.invoices.extraction.ExtractionResult;
import com.ella.backend.services.invoices.extraction.InvoiceExtractionHeuristics;
import com.ella.backend.services.invoices.parsers.ParseResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadService {

    private static final String BUILD_MARKER = "2025-12-29T1022";

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;
    private final ClassificationService classificationService;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;
    private final ExtractionPipeline extractionPipeline;
    
    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password, String dueDate) {
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
                boolean allowExactDuplicates = isNubank(parsed);
                transactions = deduplicateTransactions(transactions, allowExactDuplicates);
            } else {
                transactions = parseCsv(is);
                transactions = deduplicateTransactions(transactions);
            }

            if (transactions == null || transactions.isEmpty()) {
                if (isPdf) {
                    throw new IllegalArgumentException(
                        (password != null && !password.isBlank()
                            ? "Não foi possível extrair transações desse PDF mesmo com senha informada. "
                            : "Não foi possível extrair transações desse PDF. ") +
                            "Ele pode estar escaneado (imagem), ter restrição de extração de texto, " +
                            "ou ter um layout ainda não suportado. " +
                            "Tente exportar/enviar um CSV, ou um PDF com texto selecionável."
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
            throw new RuntimeException("Failed to process file", e);
        }
    }

    private record ParsedPdfResult(List<TransactionData> transactions, ParseResult parseResult, String rawText) {}

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

        String instN = tx.installmentNumber != null ? tx.installmentNumber.toString() : "";
        String instT = tx.installmentTotal != null ? tx.installmentTotal.toString() : "";

        return purchaseDate + "|" + dueDate + "|" + amount + "|" + type + "|" + scope + "|" + desc + "|" + cardName + "|" + instN + "/" + instT;
    }

    private static String normalizeKeyField(String value) {
        if (value == null) return "";
        String v = value.trim();
        if (v.isEmpty()) return "";
        v = v.replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        return v;
    }

    private boolean isNubank(ParsedPdfResult parsed) {
        if (parsed == null) return false;

        String bank = parsed.parseResult() != null ? parsed.parseResult().getBankName() : null;
        if (bank != null && bank.toLowerCase(java.util.Locale.ROOT).contains("nubank")) return true;

        String raw = parsed.rawText();
        if (raw == null) return false;
        String n = raw.toLowerCase(java.util.Locale.ROOT);
        return n.contains("nubank") || n.contains("nu pagamentos") || n.contains("nu bank");
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
        try {
            ExtractionResult result = extractionPipeline.extractFromPdf(inputStream, password, dueDateOverride);
            List<TransactionData> mapped = new ArrayList<>();
            for (com.ella.backend.services.invoices.parsers.TransactionData p : result.transactions()) {
                InstallmentInfo installment = (p.installmentNumber != null && p.installmentTotal != null)
                        ? new InstallmentInfo(p.installmentNumber, p.installmentTotal)
                        : null;

                TransactionData data = new TransactionData(
                        p.description,
                        p.amount,
                        p.type,
                        p.category != null ? p.category : "Outros",
                        p.date,
                        p.cardName,
                        p.scope,
                        installment
                );
                data.dueDate = p.dueDate;
                mapped.add(data);
            }

            return new ParsedPdfResult(mapped, result.parseResult(), result.rawText());
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
    }

    private List<TransactionData> parsePdf(InputStream inputStream, String password) throws IOException {
        return parsePdf(inputStream, password, null);
    }

    static boolean isLikelyGarbledMerchant(String description) {
        return InvoiceExtractionHeuristics.isLikelyGarbledMerchant(description);
    }

    private InvoiceUploadResponseDTO processTransactions(List<TransactionData> transactions, String originalFilename) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new IllegalStateException("Usuário não autenticado");
        User user = userService.findByEmail(auth.getName());

        List<FinancialTransactionResponseDTO> responseTransactions = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        Invoice lastInvoice = null;
        
        // Cache para evitar múltiplas consultas/criações do mesmo cartão no mesmo upload
        Map<String, CreditCard> cardCache = new HashMap<>();

        for (TransactionData data : transactions) {
             CardMetadata cardMetadata = extractCardMetadata(data.cardName, originalFilename);
             String cacheKey = (cardMetadata.brand() + "|" + (cardMetadata.lastFourDigits() != null ? cardMetadata.lastFourDigits() : cardMetadata.name())).toLowerCase();
             
             CreditCard card = cardCache.computeIfAbsent(cacheKey, key -> resolveOrCreateCard(user, cardMetadata));
             
             if (card != null) {
                 LocalDate invoiceDueDate = data.dueDate;
                 if (invoiceDueDate == null) {
                     // Fallback: se não conseguimos extrair vencimento do arquivo, usamos a lógica antiga (baseada na data da compra)
                     invoiceDueDate = estimateDueDateFromCardAndTxDate(card, data.date);
                 }

                 Invoice invoice = getOrCreateInvoice(card, invoiceDueDate);
                 lastInvoice = invoice;
                 
                 FinancialTransaction tx = saveTransaction(user, data, invoiceDueDate);
                 createOrLinkInstallment(invoice, tx, data);
                 
                 if (data.type == TransactionType.EXPENSE) {
                     invoice.setTotalAmount(invoice.getTotalAmount().add(data.amount));
                 } else {
                     invoice.setTotalAmount(invoice.getTotalAmount().subtract(data.amount));
                 }
                 invoiceRepository.save(invoice);
                 
                 responseTransactions.add(mapToDTO(tx));
                 totalAmount = totalAmount.add(tx.getAmount());
             }
        }
        
        LocalDate startDate = transactions.stream().map(t -> t.date).min(LocalDate::compareTo).orElse(null);
        LocalDate endDate = transactions.stream().map(t -> t.date).max(LocalDate::compareTo).orElse(null);

        return InvoiceUploadResponseDTO.builder()
                .invoiceId(lastInvoice != null ? lastInvoice.getId() : null)
                .totalAmount(totalAmount)
                .totalTransactions(responseTransactions.size())
                .startDate(startDate)
                .endDate(endDate)
                .transactions(responseTransactions)
                .build();
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

    private LocalDate estimateDueDateFromCardAndTxDate(CreditCard card, LocalDate txDate) {
        if (txDate == null) return null;
        Integer dueDay = null;
        try {
            dueDay = card != null ? card.getDueDay() : null;
        } catch (Exception ignored) {}

        int safeDueDay = (dueDay != null && dueDay >= 1 && dueDay <= 28) ? dueDay : 10;
        LocalDate base = LocalDate.of(txDate.getYear(), txDate.getMonthValue(), 1);
        LocalDate nextMonth = base.plusMonths(1);
        int dom = Math.min(safeDueDay, nextMonth.lengthOfMonth());
        return nextMonth.withDayOfMonth(dom);
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
            tx.getTripId() != null ? tx.getTripId().toString() : null,
            tx.getTripSubcategory(),
            tx.getTransactionDate(),
            tx.getPurchaseDate(),
            tx.getDueDate(),
            tx.getPaidDate(),
            tx.getStatus(),
            tx.getCreatedAt(),
            tx.getUpdatedAt()
        );
    }

    private CreditCard resolveOrCreateCard(User user, CardMetadata cardMetadata) {
        try {
            var cards = creditCardRepository.findByOwner(user);
            if (cards != null) {
                // Prioriza match por últimos 4 dígitos + bandeira
                if (cardMetadata.lastFourDigits() != null) {
                    for (var c : cards) {
                        if (c.getLastFourDigits() != null
                                && c.getLastFourDigits().equals(cardMetadata.lastFourDigits())
                                && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                            return c;
                        }
                    }
                }

                // Fallback: nome + bandeira
                for (var c : cards) {
                    if (equalsIgnoreCase(c.getName(), cardMetadata.name())
                            && equalsIgnoreCase(c.getBrand(), cardMetadata.brand())) {
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {}

        CreditCard cc = new CreditCard();
        cc.setOwner(user);
        cc.setName(cardMetadata.name());
        cc.setBrand(cardMetadata.brand());
        cc.setLastFourDigits(cardMetadata.lastFourDigits());
        cc.setLimitAmount(java.math.BigDecimal.valueOf(10000));
        cc.setClosingDay(5);
        cc.setDueDay(15);
        return creditCardRepository.save(cc);
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
                return new TransactionData(description, amount.abs(), type, category, date, cardName, scope, installment);

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
                return new TransactionData(description, amount.abs(), type, category, date, null, scope, installment);

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
                return new TransactionData(description, amount, type, category, date, null, scope, installment);
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CSV] Erro ao parsear linha: " + line + " - " + e.getMessage());
            return null;
        }
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
                    // Tenta formato dd/MM/yy
                    DateTimeFormatter shortYear = DateTimeFormatter.ofPattern("dd/MM/yy");
                    LocalDate parsed = LocalDate.parse(dateStr, shortYear);
                    if (parsed.isAfter(LocalDate.now().plusMonths(1))) {
                        return parsed.minusYears(1);
                    }
                    return parsed;
                } catch (Exception ignored) {
                    // segue para dd/MM
                }

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
        String d = description == null ? "" : description.toLowerCase();
        if (type == TransactionType.INCOME) {
            // Para fatura de cartão, créditos podem ser pagamento/estorno; manter genérico.
            return "Outros";
        } else {
            if (d.contains("uber") || d.contains(" 99") || d.contains("99 ") || d.contains("cabify")) return "Transporte";
            if (d.contains("posto") || d.contains("combust") || d.contains("ipiranga") || d.contains("shell") || d.contains("petro")) return "Transporte";
            if (d.contains("ifood") || d.contains("ubereats") || d.contains("restaurante") || d.contains("pizza") || d.contains("padaria") || d.contains("lanchonete")) return "Alimentação";
            if (d.contains("mercado") || d.contains("supermerc") || d.contains("carrefour") || d.contains("pão de açúcar") || d.contains("pao de acucar") || d.contains("assai") || d.contains("atacado")) return "Mercado";
            if (d.contains("netflix") || d.contains("spotify") || d.contains("youtube") || d.contains("prime") || d.contains("disney") || d.contains("hbo")) return "Streaming";
            if (d.contains("farmac") || d.contains("drog") || d.contains("droga") || d.contains("drogaria")) return "Farmácia";
            if (d.contains("hospital") || d.contains("clinica") || d.contains("consulta") || d.contains("medic") || d.contains("otica") || d.contains("ótica")) return "Saúde";
            if (d.contains("academia") || d.contains("gym") || d.contains("smartfit") || d.contains("bluefit")) return "Lazer";
            if (d.contains("internet")) return "Internet";
            if (d.contains("telefone") || d.contains("celular")) return "Celular";
            if (d.contains("aluguel") || d.contains("rent")) return "Aluguel";
            if (d.contains("agua") || d.contains("água")) return "Água";
            if (d.contains("energia") || d.contains("luz")) return "Luz";
            return "Outros";
        }
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

    private static class TransactionData {
        String description;
        BigDecimal amount;
        TransactionType type;
        TransactionScope scope;
        String category;
        LocalDate date;
        LocalDate dueDate;
        String cardName;
        Integer installmentNumber;
        Integer installmentTotal;

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date, String cardName, TransactionScope scope, InstallmentInfo installmentInfo) {
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.scope = scope;
            this.category = category;
            this.date = date;
            this.dueDate = null;
            this.cardName = cardName;
            if (installmentInfo != null) {
                this.installmentNumber = installmentInfo.number();
                this.installmentTotal = installmentInfo.total();
            }
        }
    }

    private record InstallmentInfo(Integer number, Integer total) {}

    private record CardMetadata(String name, String brand, String lastFourDigits) {}
}





