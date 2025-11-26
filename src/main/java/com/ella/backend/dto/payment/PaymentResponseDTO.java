package com.ella.backend.dto.payment;

import com.ella.backend.enums.Currency;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Plan;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentResponseDTO {

    private String id;
    private String userId;
    private String userName;

    private Plan plan;
    private BigDecimal amount;
    private Currency currency;

    private PaymentStatus status;
    private PaymentProvider provider;

    private String providerPaymentId;
    private String providerRawStatus;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
