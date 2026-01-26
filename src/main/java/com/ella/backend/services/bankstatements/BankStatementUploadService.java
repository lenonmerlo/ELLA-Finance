package com.ella.backend.services.bankstatements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.dto.BankStatementUploadResponseDTO;
import com.ella.backend.entities.BankStatement;
import com.ella.backend.entities.BankStatementTransaction;
import com.ella.backend.repositories.BankStatementRepository;
import com.ella.backend.services.bankstatements.parsers.ItauBankStatementParser;
import com.ella.backend.services.ocr.PdfTextExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankStatementUploadService {

    private static boolean isParserDebugEnabled() {
        String fromProp = System.getProperty("ELLA_PARSER_DEBUG");
        if (fromProp != null) {
            return Boolean.parseBoolean(fromProp);
        }
        String fromEnv = System.getenv("ELLA_PARSER_DEBUG");
        return Boolean.parseBoolean(fromEnv != null ? fromEnv : "false");
    }

    private final BankStatementRepository bankStatementRepository;
    private final PdfTextExtractor pdfTextExtractor;

    @Transactional
    public BankStatementUploadResponseDTO uploadItauPdf(MultipartFile file, UUID userId, String password) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo ausente ou vazio");
        }
        if (userId == null) {
            throw new IllegalArgumentException("Usuário inválido");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Somente PDF é suportado para extrato bancário Itaú");
        }

        byte[] pdfBytes;
        try (InputStream is = file.getInputStream()) {
            pdfBytes = is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler arquivo PDF", e);
        }

        String text;
        try (PDDocument document = (password != null && !password.isBlank())
                ? PDDocument.load(new ByteArrayInputStream(pdfBytes), password)
                : PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            try {
                document.setAllSecurityToBeRemoved(true);
            } catch (Exception ignored) {
            }

            // For statements, positional sorting often preserves table row order better.
            text = safeExtractTextSorted(document);
            if (text == null || text.isBlank()) {
                text = safeExtractText(document);
            }
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            if (password != null && !password.isBlank()) {
                throw new IllegalArgumentException("Senha incorreta para o arquivo PDF.");
            }
            throw new IllegalArgumentException("Arquivo PDF protegido por senha. Informe a senha.");
        } catch (Exception e) {
            throw new RuntimeException("Falha ao abrir/processar PDF", e);
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Não foi possível extrair texto do PDF. Ele pode estar escaneado (imagem) ou com restrição de extração.");
        }

        boolean debug = isParserDebugEnabled();
        if (debug) {
            System.err.println("[UPLOAD_DEBUG] Texto extraído do PDF (" + text.length() + " chars):");
            System.err.println("[UPLOAD_DEBUG] Primeiros 500 chars: " + text.substring(0, Math.min(500, text.length())));
        }

        ItauBankStatementParser parser = new ItauBankStatementParser();
        var parsed = parser.parse(text);

        if (debug) {
            int parsedCount = parsed.getTransactions() == null ? 0 : parsed.getTransactions().size();
            System.err.println("[UPLOAD_DEBUG] Parser retornou " + parsedCount + " transações");
            System.err.println("[UPLOAD_DEBUG] Opening balance: " + parsed.getOpeningBalance());
            System.err.println("[UPLOAD_DEBUG] Closing balance: " + parsed.getClosingBalance());
        }

        log.info("[BankStatementUpload] parsed statementDate={} opening={} closing={} rawTxCount={} ",
            parsed.getStatementDate(), parsed.getOpeningBalance(), parsed.getClosingBalance(),
            parsed.getTransactions() != null ? parsed.getTransactions().size() : 0);

        if (parsed.getTransactions() != null && !parsed.getTransactions().isEmpty()) {
            long balanceRows = parsed.getTransactions().stream().filter(t -> t.type() == BankStatementTransaction.Type.BALANCE).count();
            long debitRows = parsed.getTransactions().stream().filter(t -> t.type() == BankStatementTransaction.Type.DEBIT).count();
            long creditRows = parsed.getTransactions().stream().filter(t -> t.type() == BankStatementTransaction.Type.CREDIT).count();

            log.info("[BankStatementUpload] parsed breakdown: DEBIT={}, CREDIT={}, BALANCE={}", debitRows, creditRows, balanceRows);
            log.info("[BankStatementUpload] parsed samples={}",
                parsed.getTransactions().stream()
                    .limit(5)
                    .map(t -> t.transactionDate() + " | " + t.type() + " | " + t.amount() + " | bal=" + t.balance() + " | " + t.description())
                    .toList());
        }

        BankStatement statement = new BankStatement();
        statement.setUserId(userId);
        statement.setBank("ITAU");
        statement.setStatementDate(parsed.getStatementDate());
        statement.setOpeningBalance(nz(parsed.getOpeningBalance()));
        statement.setClosingBalance(nz(parsed.getClosingBalance()));
        statement.setCreditLimit(nz(parsed.getCreditLimit()));
        statement.setAvailableLimit(nz(parsed.getAvailableLimit()));

        int persisted = 0;

        for (var tx : parsed.getTransactions()) {
            if (tx == null) continue;

            if (tx.type() == BankStatementTransaction.Type.BALANCE) {
                if (debug) {
                    System.err.println("[UPLOAD_DEBUG] Ignorando BALANCE: " + tx.description());
                }
                continue;
            }

            BankStatementTransaction entity = new BankStatementTransaction();
            entity.setTransactionDate(tx.transactionDate());
            entity.setDescription(tx.description() == null ? "" : tx.description());
            entity.setType(tx.type() == null ? BankStatementTransaction.Type.DEBIT : tx.type());
            entity.setAmount(nz(tx.amount()));
            entity.setBalance(nz(tx.balance()));

            statement.addTransaction(entity);
            persisted++;

            if (debug) {
                System.err.println("[UPLOAD_DEBUG] Adicionando transação: "
                        + tx.transactionDate() + " | " + tx.description() + " | " + tx.amount() + " | " + tx.type());
            }
        }

        log.info("[BankStatementUpload] persisting {} transactions (excluding BALANCE)", persisted);

        if (debug) {
            System.err.println("[UPLOAD_DEBUG] Total de transações adicionadas: " + persisted);
        }

        BankStatement saved = bankStatementRepository.save(statement);

        if (debug) {
            System.err.println("[UPLOAD_DEBUG] BankStatement salvo com ID: " + saved.getId());
        }

        return BankStatementUploadResponseDTO.from(saved);
    }

    private String safeExtractText(PDDocument document) {
        try {
            return pdfTextExtractor.extractText(document);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String safeExtractTextSorted(PDDocument document) {
        try {
            return pdfTextExtractor.extractTextSorted(document);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
