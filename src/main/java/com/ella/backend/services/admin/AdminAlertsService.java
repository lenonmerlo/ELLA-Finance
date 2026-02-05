package com.ella.backend.services.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.admin.AdminAlertDTO;
import com.ella.backend.dto.admin.AdminAlertsListItemDTO;
import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.AdminAlertSeverity;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.SubscriptionStatus;
import com.ella.backend.repositories.PaymentRepository;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAlertsService {

    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 100;
    private static final int EXPIRING_SOON_DAYS = 7;
    private static final int GRACE_DAYS_FOR_RECENT_PAYMENT = 15;
    private static final int FALLBACK_RECENT_PAYMENT_DAYS = 45;

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    @SuppressWarnings("null")
    public List<AdminAlertsListItemDTO> listAlerts(String q, Integer limit) {
        int safeLimit = limit == null ? DEFAULT_LIMIT : Math.min(MAX_LIMIT, Math.max(1, limit));

        PageRequest pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userRepository.search(q, Role.USER, null, pageable);
        List<User> content = users.getContent();
        if (content.isEmpty()) return List.of();

        List<UUID> userIds = content.stream().map(User::getId).toList();

        Map<UUID, Subscription> subscriptionByUserId = subscriptionRepository.findByUserIdIn(userIds)
                .stream()
                .filter(s -> s.getUser() != null && s.getUser().getId() != null)
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity(), (a, b) -> a));

        Map<UUID, PaymentRepository.LastPaymentInfo> lastPaymentByUserId = paymentRepository
                .findLastPaidAtByUserIds(userIds, PaymentStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(PaymentRepository.LastPaymentInfo::getUserId, Function.identity(), (a, b) -> a));

        LocalDate today = LocalDate.now();

        List<AdminAlertsListItemDTO> items = new ArrayList<>();
        for (User user : content) {
            Subscription subscription = subscriptionByUserId.get(user.getId());
            PaymentRepository.LastPaymentInfo lastPayment = lastPaymentByUserId.get(user.getId());
            List<AdminAlertDTO> alerts = computeAlerts(today, user, subscription, lastPayment);
            if (alerts.isEmpty()) continue;

            AdminAlertsListItemDTO dto = new AdminAlertsListItemDTO();
            dto.setUserId(user.getId().toString());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setUserPlan(user.getPlan());
            dto.setUserStatus(user.getStatus());

            if (subscription != null) {
                dto.setSubscriptionStatus(subscription.getStatus());
                dto.setSubscriptionPlan(subscription.getPlan());
                dto.setSubscriptionStartDate(subscription.getStartDate());
                dto.setSubscriptionEndDate(subscription.getEndDate());
                dto.setSubscriptionAutoRenew(subscription.isAutoRenew());
            }
            if (lastPayment != null) {
                dto.setLastPaidAt(lastPayment.getLastPaidAt());
            }
            dto.setAlerts(alerts);
            items.add(dto);
        }

        items.sort(Comparator
                .comparing((AdminAlertsListItemDTO i) -> maxSeverityRank(i.getAlerts())).reversed()
                .thenComparing(i -> daysToExpire(today, i.getSubscriptionEndDate())));

        return items;
    }

    private int maxSeverityRank(List<AdminAlertDTO> alerts) {
        if (alerts == null || alerts.isEmpty()) return 0;
        int max = 0;
        for (AdminAlertDTO a : alerts) {
            int r = severityRank(a.getSeverity());
            if (r > max) max = r;
        }
        return max;
    }

    private int severityRank(AdminAlertSeverity severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case DANGER -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    private long daysToExpire(LocalDate today, LocalDate endDate) {
        if (endDate == null) return Long.MAX_VALUE;
        return ChronoUnit.DAYS.between(today, endDate);
    }

    private List<AdminAlertDTO> computeAlerts(LocalDate today, User user, Subscription subscription,
            PaymentRepository.LastPaymentInfo lastPayment) {
        List<AdminAlertDTO> alerts = new ArrayList<>();

        Plan userPlan = user.getPlan();
        boolean isPaidPlan = userPlan != null && userPlan != Plan.FREE;

        if (isPaidPlan && subscription == null) {
            alerts.add(new AdminAlertDTO(
                    "PAID_PLAN_NO_SUBSCRIPTION",
                    AdminAlertSeverity.DANGER,
                    "Plano pago sem assinatura",
                    "Usuário está em plano pago mas não possui assinatura cadastrada."
            ));
            return alerts;
        }

        if (subscription == null) return alerts;

        if (subscription.getStatus() == SubscriptionStatus.CANCELED && isPaidPlan) {
            alerts.add(new AdminAlertDTO(
                    "SUBSCRIPTION_CANCELED",
                    AdminAlertSeverity.WARNING,
                    "Assinatura cancelada",
                    "Plano do usuário está pago, mas a assinatura está cancelada."
            ));
        }

        if (subscription.getEndDate() == null && isPaidPlan) {
            alerts.add(new AdminAlertDTO(
                    "SUBSCRIPTION_MISSING_END_DATE",
                    AdminAlertSeverity.DANGER,
                    "Assinatura sem data de término",
                    "A assinatura não possui endDate; isso impede validar vencimento."
            ));
            return alerts;
        }

        if (isPaidPlan && subscription.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(today, subscription.getEndDate());
            if (days < 0 && subscription.getStatus() != SubscriptionStatus.CANCELED) {
                alerts.add(new AdminAlertDTO(
                        "SUBSCRIPTION_OVERDUE",
                        AdminAlertSeverity.DANGER,
                        "Assinatura vencida",
                        "A assinatura está vencida."
                ));
            } else if (days <= EXPIRING_SOON_DAYS) {
                alerts.add(new AdminAlertDTO(
                        "SUBSCRIPTION_EXPIRING_SOON",
                        AdminAlertSeverity.WARNING,
                        "Assinatura vencendo",
                        "Vence em " + days + " dia(s)."
                ));
            }
        }

        if (!subscription.isAutoRenew()
                && isPaidPlan
                && subscription.getEndDate() != null
                && !subscription.getEndDate().isBefore(today)
                && subscription.getStatus() != SubscriptionStatus.CANCELED) {
            alerts.add(new AdminAlertDTO(
                    "AUTO_RENEW_DISABLED",
                    AdminAlertSeverity.INFO,
                    "Auto-renovação desligada",
                    "A assinatura está ativa, mas não está configurada para auto-renovar."
            ));
        }

        if (isPaidPlan && subscription.getEndDate() != null && !subscription.getEndDate().isBefore(today)) {
            LocalDateTime lastPaidAt = lastPayment == null ? null : lastPayment.getLastPaidAt();
            Long subscriptionDays = null;
            if (subscription.getStartDate() != null) {
                subscriptionDays = ChronoUnit.DAYS.between(subscription.getStartDate(), subscription.getEndDate());
            }

            int recentWindowDays = FALLBACK_RECENT_PAYMENT_DAYS;
            if (subscriptionDays != null && subscriptionDays > 0 && subscriptionDays <= 45) {
                recentWindowDays = (int) Math.min(60, subscriptionDays + GRACE_DAYS_FOR_RECENT_PAYMENT);
            }

            if (lastPaidAt == null) {
                alerts.add(new AdminAlertDTO(
                        "NO_PAYMENT_REGISTERED",
                        AdminAlertSeverity.INFO,
                        "Sem pagamento registrado",
                        "Não há pagamento aprovado registrado para esta assinatura (registro manual pode estar faltando)."
                ));
            } else {
                long daysSince = ChronoUnit.DAYS.between(lastPaidAt.toLocalDate(), today);
                if (daysSince > recentWindowDays) {
                    alerts.add(new AdminAlertDTO(
                            "PAYMENT_NOT_RECENT",
                            AdminAlertSeverity.WARNING,
                            "Pagamento antigo",
                            "Último pagamento aprovado foi há " + daysSince + " dia(s)."
                    ));
                }
            }
        }

        return alerts;
    }
}
