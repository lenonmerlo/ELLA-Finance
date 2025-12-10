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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceUploadService {

    private final FinancialTransactionRepository transactionRepository;
    private final UserService userService;
    private final CreditCardRepository creditCardRepository;
    private final InvoiceRepository invoiceRepository;
    private final InstallmentRepository installmentRepository;

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public InvoiceUploadResponseDTO processInvoice(MultipartFile file, String password) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        try (InputStream is = file.getInputStream()) {
            List<TransactionData> transactions;
            if (filename.toLowerCase().endsWith(".pdf")) {
                transactions = parsePdf(is, password);
            } else {
                transactions = parseCsv(is);
            }
            return processTransactions(transactions, filename);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process file", e);
        }
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

    private List<TransactionData> parsePdf(InputStream inputStream, String password) throws IOException {
        List<TransactionData> transactions = new ArrayList<>();
        try (PDDocument document = (password != null && !password.isBlank()) ? PDDocument.load(inputStream, password) : PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("[InvoiceUpload] PDF extracted sample: {}", (text.length() > 500 ? text.substring(0, 500) : text));
            
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                TransactionData data = parsePdfLine(line);
                if (data != null) {
                    transactions.add(data);
                } else {
                    // Log lines that didn't match to help debugging
                    if (line.matches(".*\\d{2}/\\d{2}.*")) { // Only log lines that look like they might have a date
                        log.debug("[InvoiceUpload] Ignored line (regex mismatch): {}", line);
                    }
                }
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("O arquivo PDF está protegido por senha. Por favor, forneça a senha.");
        }
        log.info("[InvoiceUpload] Total parsed transactions: {}", transactions.size());
        return transactions;
    }

    private TransactionData parsePdfLine(String line) {
        try {
            line = line.trim();
            if (line.isEmpty()) return null;
            // Regex genérico para tentar capturar data, descrição e valor
            // Suporta: 01/01/2023, 01/01, 01 JAN
            Pattern pattern = Pattern.compile("^(\\d{2}\\s+[A-Za-z]{3}|\\d{2}/\\d{2}(?:/\\d{4})?)\\s+(.+?)\\s+(-?[\\d\\.,]+)\\s*$");
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                String desc = matcher.group(2);
                String amountStr = matcher.group(3);
                
                log.debug("[InvoiceUpload] Matched line desc='{}' amount='{}'", desc, amountStr);

                LocalDate date = parseDate(dateStr);
                
                // Limpar valor
                amountStr = amountStr.replace(".", "").replace(",", ".");
                BigDecimal amount = new BigDecimal(amountStr);
                
                // Lógica invertida para faturas de cartão:
                // Valores positivos são DESPESAS (compras)
                // Valores negativos são CRÉDITOS (pagamentos/estornos)
                TransactionType type;
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    type = TransactionType.INCOME; // Crédito na fatura
                } else {
                    // Verifica se é um pagamento explícito
                    if (desc.toLowerCase().contains("pagamento") || desc.toLowerCase().contains("payment")) {
                        type = TransactionType.INCOME;
                    } else {
                        type = TransactionType.EXPENSE;
                    }
                }
                
                String category = categorizeDescription(desc, type);
                TransactionScope scope = inferScope(desc, null);
                
                return new TransactionData(desc, amount.abs(), type, category, date, null, scope);
            }
        } catch (Exception ignored) {}
        return null;
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
             String cardName = data.cardName != null && !data.cardName.isEmpty() ? data.cardName : inferCardName(originalFilename);
             
             CreditCard card = cardCache.computeIfAbsent(cardName, name -> resolveOrCreateCard(user, name));
             
             if (card != null) {
                 Invoice invoice = getOrCreateInvoice(card, data.date);
                 lastInvoice = invoice;
                 
                 FinancialTransaction tx = saveTransaction(user, data, originalFilename);
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

    private FinancialTransaction saveTransaction(User user, TransactionData txData, String originalFilename) {
        FinancialTransaction entity = FinancialTransaction.builder()
                .person(user)
                .description(txData.description)
                .amount(txData.amount)
                .type(txData.type)
                .scope(txData.scope != null ? txData.scope : TransactionScope.PERSONAL)
                .category(txData.category)
                .transactionDate(txData.date)
                .status(TransactionStatus.PENDING)
                .build();
        return Objects.requireNonNull(transactionRepository.save(entity));
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
            tx.getTransactionDate(),
            tx.getDueDate(),
            tx.getPaidDate(),
            tx.getStatus(),
            tx.getCreatedAt(),
            tx.getUpdatedAt()
        );
    }

    private CreditCard resolveOrCreateCard(User user, String cardName) {
        // cardName já foi inferido ou extraído
        try {
            var cards = creditCardRepository.findByOwner(user);
            if (cards != null) {
                for (var c : cards) {
                    if (c.getName() != null && c.getName().equalsIgnoreCase(cardName)) {
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {}
        
        CreditCard cc = new CreditCard();
        cc.setOwner(user);
        cc.setName(cardName);
        cc.setBrand(cardName);
        cc.setLastFourDigits("0000");
        cc.setLimitAmount(java.math.BigDecimal.valueOf(10000));
        cc.setClosingDay(5);
        cc.setDueDay(15);
        return creditCardRepository.save(cc);
    }

    private String inferCardName(String filename) {
        if (filename == null || filename.isBlank()) return "Cartão Padrão";
        String f = filename.toLowerCase();
        if (f.contains("nubank")) return "Nubank";
        if (f.contains("visa")) return "Visa";
        if (f.contains("mastercard") || f.contains("master")) return "Mastercard";
        if (f.contains("itau")) return "Itaú";
        if (f.contains("bradesco")) return "Bradesco";
        if (f.contains("santander")) return "Santander";
        if (f.contains("c6")) return "C6 Bank";
        if (f.contains("inter")) return "Banco Inter";
        return filename;
    }

    private Invoice getOrCreateInvoice(CreditCard card, LocalDate txDate) {
        int month = txDate.getMonthValue();
        int year = txDate.getYear();
        return invoiceRepository
                .findByCardAndMonthAndYear(card, month, year)
                .orElseGet(() -> {
                    Invoice inv = new Invoice();
                    inv.setCard(card);
                    inv.setMonth(month);
                    inv.setYear(year);
                    LocalDate due = LocalDate.of(year, month, 1).plusMonths(1).withDayOfMonth(Math.min(10, LocalDate.of(year, month, 1).plusMonths(1).lengthOfMonth()));
                    inv.setDueDate(due);
                    inv.setTotalAmount(BigDecimal.ZERO);
                    inv.setPaidAmount(BigDecimal.ZERO);
                    return invoiceRepository.save(inv);
                });
    }

    private void createOrLinkInstallment(Invoice invoice, FinancialTransaction entity, TransactionData txData) {
        try {
            var existing = installmentRepository.findByTransaction(entity);
            if (existing != null && !existing.isEmpty()) {
                var inst = existing.get(0);
                if (!invoice.equals(inst.getInvoice())) {
                    inst.setInvoice(invoice);
                    installmentRepository.save(inst);
                }
                return;
            }
        } catch (Exception ignored) {}

        var installment = new com.ella.backend.entities.Installment();
        installment.setInvoice(invoice);
        installment.setTransaction(entity);
        installment.setNumber(1);
        installment.setTotal(1);
        installment.setAmount(txData.amount);
        installment.setDueDate(txData.date);
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
                return new TransactionData(description, amount.abs(), type, category, date, cardName, scope);

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
                return new TransactionData(description, amount.abs(), type, category, date, null, scope);

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
                return new TransactionData(description, amount, type, category, date, null, scope);
            }
            return null;
        } catch (Exception e) {
            System.err.println("[CSV] Erro ao parsear linha: " + line + " - " + e.getMessage());
            return null;
        }
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
        if (category == null) return "Other";
        String c = category.toLowerCase();
        if (c.contains("transporte")) return "Transport";
        if (c.contains("alimentação") || c.contains("alimentacao")) return "Food";
        if (c.contains("entretenimento")) return "Entertainment";
        if (c.contains("saúde") || c.contains("saude")) return "Health";
        if (c.contains("compras")) return "Shopping";
        if (c.contains("casa")) return "Housing";
        if (c.contains("vestuário") || c.contains("vestuario")) return "Clothing";
        return category;
    }

    private String categorizeDescription(String description, TransactionType type) {
        String d = description == null ? "" : description.toLowerCase();
        if (type == TransactionType.INCOME) {
            if (d.contains("salary") || d.contains("salário") || d.contains("payroll")) return "Income";
            if (d.contains("bonus") || d.contains("bônus")) return "Income";
            return "Income";
        } else {
            if (d.contains("uber") || d.contains("99") || d.contains("lyft") || d.contains("cabify")) return "Transport";
            if (d.contains("posto") || d.contains("combust") || d.contains("ipiranga") || d.contains("shell")) return "Transport";
            if (d.contains("ifood") || d.contains("ubereats") || d.contains("restaurante") || d.contains("pizza") || d.contains("padaria")) return "Food";
            if (d.contains("mercado") || d.contains("supermarket") || d.contains("carrefour") || d.contains("pão de açúcar") || d.contains("assai") || d.contains("atacado")) return "Groceries";
            if (d.contains("netflix") || d.contains("spotify") || d.contains("youtube") || d.contains("prime") || d.contains("disney")) return "Entertainment";
            if (d.contains("zara") || d.contains("renner") || d.contains("c&a") || d.contains("shein") || d.contains("roupa")) return "Clothing";
            if (d.contains("farmac") || d.contains("drog") || d.contains("academia") || d.contains("gym") || d.contains("smartfit")) return "Health";
            if (d.contains("amazon") || d.contains("mercado livre") || d.contains("shopee") || d.contains("aliexpress")) return "Shopping";
            if (d.contains("casa") || d.contains("home") || d.contains("aluguel") || d.contains("rent") || d.contains("constru")) return "Housing";
            return "Other";
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
        String cardName;

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date) {
            this(description, amount, type, category, date, null, TransactionScope.PERSONAL);
        }

        TransactionData(String description, BigDecimal amount, TransactionType type, String category, LocalDate date, String cardName, TransactionScope scope) {
            this.description = description;
            this.amount = amount;
            this.type = type;
            this.scope = scope;
            this.category = category;
            this.date = date;
            this.cardName = cardName;
        }
    }
}
