package com.ella.backend.services.admin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.dto.admin.AdminBillingListItemDTO;
import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.BillingStatus;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Role;
import com.ella.backend.repositories.PaymentRepository;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminBillingService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    @SuppressWarnings("null")
    public Page<AdminBillingListItemDTO> listCustomers(String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<User> users = userRepository.search(q, Role.USER, null, pageable);
        List<User> content = users.getContent();

        if (content.isEmpty()) {
            return users.map(u -> new AdminBillingListItemDTO());
        }

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

        List<AdminBillingListItemDTO> dtos = content.stream().map(user -> {
            AdminBillingListItemDTO dto = new AdminBillingListItemDTO();
            dto.setUserId(user.getId().toString());
            dto.setName(user.getName());
            dto.setEmail(user.getEmail());
            dto.setUserPlan(user.getPlan());
            dto.setUserStatus(user.getStatus());

            Subscription subscription = subscriptionByUserId.get(user.getId());
            if (subscription == null) {
                dto.setBillingStatus(BillingStatus.NO_SUBSCRIPTION);
                dto.setDaysToExpire(Long.MIN_VALUE);
            } else {
                dto.setSubscriptionStatus(subscription.getStatus());
                dto.setSubscriptionPlan(subscription.getPlan());
                dto.setSubscriptionStartDate(subscription.getStartDate());
                dto.setSubscriptionEndDate(subscription.getEndDate());

                BillingStatus billingStatus = computeBillingStatus(today, subscription);
                dto.setBillingStatus(billingStatus);

                if (subscription.getEndDate() != null) {
                    dto.setDaysToExpire(ChronoUnit.DAYS.between(today, subscription.getEndDate()));
                }
            }

            PaymentRepository.LastPaymentInfo lastPayment = lastPaymentByUserId.get(user.getId());
            if (lastPayment != null) {
                dto.setLastPaidAt(lastPayment.getLastPaidAt());
            }

            return dto;
        }).toList();

        return new PageImpl<>(dtos, users.getPageable(), users.getTotalElements());
    }

    private BillingStatus computeBillingStatus(LocalDate today, Subscription subscription) {
        if (subscription.getStatus() != null && subscription.getStatus().name().equals("CANCELED")) {
            return BillingStatus.CANCELED;
        }

        if (subscription.getEndDate() == null) {
            return BillingStatus.OVERDUE;
        }

        if (subscription.getEndDate().isBefore(today)) {
            return BillingStatus.OVERDUE;
        }

        return BillingStatus.UP_TO_DATE;
    }
}
