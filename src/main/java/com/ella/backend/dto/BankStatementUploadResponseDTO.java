package com.ella.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.ella.backend.entities.BankStatement;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BankStatementUploadResponseDTO {

    private UUID id;
    private String bank;
    private LocalDate statementDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal creditLimit;
    private BigDecimal availableLimit;
    private List<BankStatementTransactionDTO> transactions;

    public static BankStatementUploadResponseDTO from(BankStatement statement) {
        if (statement == null) {
            return null;
        }

        List<BankStatementTransactionDTO> txs = statement.getTransactions() == null
                ? List.of()
                : statement.getTransactions().stream()
                    .sorted(Comparator.comparing(t -> t.getTransactionDate() == null ? LocalDate.MIN : t.getTransactionDate()))
                    .map(BankStatementTransactionDTO::from)
                    .toList();

        return new BankStatementUploadResponseDTO(
                statement.getId(),
                statement.getBank(),
                statement.getStatementDate(),
                statement.getOpeningBalance(),
                statement.getClosingBalance(),
                statement.getCreditLimit(),
                statement.getAvailableLimit(),
                txs
        );
    }
}
