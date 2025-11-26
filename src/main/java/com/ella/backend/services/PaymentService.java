// Rotas relacionadas: /api/payments/**
package com.ella.backend.services;

import com.ella.backend.dto.payment.PaymentResponseDTO;
import com.ella.backend.dto.payment.PaymentSimulationRequestDTO;
import com.ella.backend.entities.Payment;
import com.ella.backend.entities.Subscription;
import com.ella.backend.entities.User;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Status;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PaymentRepository;
import com.ella.backend.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    /**
     * V1: simulação de pagamento (sem integração real com gateway).
     * Futuramente, este método pode ser substituído por uma integração com Mercado Pago / Stripe.
     */
    @Transactional
    public PaymentResponseDTO simulatePayment(PaymentSimulationRequestDTO dto) {
        UUID userUuid = UUID.fromString(dto.getUserId());
        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        // Cria Payment como se tivesse sido aprovado pelo gateway
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setPlan(dto.getPlan());
        payment.setAmount(dto.getAmount());
        payment.setCurrency(dto.getCurrency());

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setProvider(dto.getProvider() != null ? dto.getProvider() : PaymentProvider.INTERNAL);
        payment.setProviderPaymentId("SIMULATED-" + System.currentTimeMillis());
        payment.setProviderRawStatus("approved");

        payment.setCreatedAt(LocalDateTime.now());
        payment.setPaidAt(LocalDateTime.now());

        Payment savedPayment = paymentRepository.save(payment);

        // Atualiza plano e status do usuário
        user.setPlan(dto.getPlan());
        user.setStatus(Status.ACTIVE);
        userRepository.save(user);

        // Cria/atualiza assinatura
        Subscription subscription = subscriptionService.createOrUpdateSubscription(user, dto.getPlan());

        // Você pode logar ou usar subscription se quiser
        return toDTO(savedPayment);
    }

    public List<PaymentResponseDTO> findByUser(String userId) {
        UUID userUuid = UUID.fromString(userId);
        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        return paymentRepository.findByUser(user)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private PaymentResponseDTO toDTO(Payment payment) {
        PaymentResponseDTO dto = new PaymentResponseDTO();

        dto.setId(payment.getId().toString());
        dto.setUserId(payment.getUser().getId().toString());
        dto.setUserName(payment.getUser().getName());

        dto.setPlan(payment.getPlan());
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());

        dto.setStatus(payment.getStatus());
        dto.setProvider(payment.getProvider());
        dto.setProviderPaymentId(payment.getProviderPaymentId());
        dto.setProviderRawStatus(payment.getProviderRawStatus());

        dto.setCreatedAt(payment.getCreatedAt());
        dto.setPaidAt(payment.getPaidAt());

        return dto;
    }
}
