package com.ella.backend.dto.dashboard;

import java.util.List;

import com.ella.backend.dto.FinancialTransactionResponseDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionListDTO {
    private List<FinancialTransactionResponseDTO> transactions;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
