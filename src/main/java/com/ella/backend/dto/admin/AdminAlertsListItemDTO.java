package com.ella.backend.dto.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Status;
import com.ella.backend.enums.SubscriptionStatus;

import lombok.Data;

@Data
public class AdminAlertsListItemDTO {
    private String userId;
    private String name;
    private String email;

    private Plan userPlan;
    private Status userStatus;

    private SubscriptionStatus subscriptionStatus;
    private Plan subscriptionPlan;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private Boolean subscriptionAutoRenew;

    private LocalDateTime lastPaidAt;

    private List<AdminAlertDTO> alerts;
}
