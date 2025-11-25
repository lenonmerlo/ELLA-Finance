package com.ella.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreditCardResponseDTO {

    private String id;

    private String ownerId;
    private String ownerName;

    private String name;
    private String brand;
    private BigDecimal limitAmount;
    private Integer closingDay;
    private Integer dueDay;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
