package com.ella.backend.dto.payment;

import com.ella.backend.enums.Plan;
import com.ella.backend.enums.SubscriptionStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SubscriptionResponseDTO {

    private String id;
    private String userId;
    private String userName;

    private Plan plan;
    private SubscriptionStatus status;

    private LocalDate startDate;
    private LocalDate endDate;

    private boolean autoRenew;
}
