package com.ella.backend.services;

import com.ella.backend.dto.payment.SubscriptionResponseDTO;
import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.SubscriptionStatus;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;
import com.ella.backend.audit.Auditable;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public SubscriptionResponseDTO getByUserId(String userId) {
        UUID uuid = UUID.fromString(userId);
        User user = userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Subscription subscription = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura não encontrada"));

        return toDTO(subscription);
    }

    /**
     * Cria ou atualiza uma assinatura simples de 30 dias.
     */
    @Auditable(action = "SUBSCRIPTION_CREATED_OR_UPDATED", entityType = "Subscription")
    public Subscription createOrUpdateSubscription(User user, com.ella.backend.enums.Plan plan) {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(30);

        Subscription subscription = subscriptionRepository.findByUser(user)
                .orElseGet(Subscription::new);

        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(start);
        subscription.setEndDate(end);
        subscription.setAutoRenew(false);

        return subscriptionRepository.save(subscription);
    }

    private SubscriptionResponseDTO toDTO(Subscription subscription) {
        SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
        dto.setId(subscription.getId().toString());
        dto.setUserId(subscription.getUser().getId().toString());
        dto.setUserName(subscription.getUser().getName());
        dto.setPlan(subscription.getPlan());
        dto.setStatus(subscription.getStatus());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setAutoRenew(subscription.isAutoRenew());
        return dto;
    }
}
