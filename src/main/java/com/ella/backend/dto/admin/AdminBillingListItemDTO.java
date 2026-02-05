package com.ella.backend.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ella.backend.enums.BillingStatus;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import com.ella.backend.enums.SubscriptionStatus;

import lombok.Data;

@Data
public class AdminBillingListItemDTO {
    private String userId;
    private String name;
    private String email;

    private Plan userPlan;
    private Status userStatus;

    private SubscriptionStatus subscriptionStatus;
    private Plan subscriptionPlan;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;

    private BillingStatus billingStatus;
    private long daysToExpire;

    private LocalDateTime lastPaidAt;
}
