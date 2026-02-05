package com.ella.backend.services.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.ella.backend.audit.Auditable;
import com.ella.backend.dto.admin.AdminCreateUserPaymentRequestDTO;
import com.ella.backend.dto.admin.AdminRenewSubscriptionRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserPlanRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserRoleRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserStatusRequestDTO;
import com.ella.backend.dto.payment.PaymentResponseDTO;
import com.ella.backend.dto.payment.SubscriptionResponseDTO;
import com.ella.backend.entities.Payment;
import com.ella.backend.entities.User;
import com.ella.backend.enums.Plan;
import com.ella.backend.enums.PaymentProvider;
import com.ella.backend.enums.PaymentStatus;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;
import com.ella.backend.enums.SubscriptionStatus;
import com.ella.backend.entities.Subscription;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PaymentRepository;
import com.ella.backend.repositories.SubscriptionRepository;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public Page<User> search(String q, Role role, Status status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        return userRepository.search(
                (q == null || q.isBlank()) ? null : q.trim(),
                role,
                status,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    public User findById(String id) {
        UUID uuid = UUID.fromString(id);
        return userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    @Auditable(action = "ADMIN_USER_UPDATE_STATUS", entityType = "User")
    public User updateStatus(String id, AdminUpdateUserStatusRequestDTO request) {
        User user = findById(id);
        user.setStatus(request.getStatus());
        return userRepository.save(user);
    }

    @Auditable(action = "ADMIN_USER_UPDATE_ROLE", entityType = "User")
    public User updateRole(String id, AdminUpdateUserRoleRequestDTO request) {
        User user = findById(id);
        user.setRole(request.getRole());
        return userRepository.save(user);
    }

    @Auditable(action = "ADMIN_USER_UPDATE_PLAN", entityType = "User")
    public User updatePlan(String id, AdminUpdateUserPlanRequestDTO request) {
        User user = findById(id);
        Plan newPlan = request.getPlan();

        user.setPlan(newPlan);
        User saved = userRepository.save(user);

        // IMPORTANT: trocar plano NÃO renova automaticamente a assinatura.
        // A renovação é uma ação separada (Admin -> Renovar assinatura).

        subscriptionRepository.findByUser(saved).ifPresent(sub -> syncExistingSubscriptionAfterPlanChange(sub, newPlan));

        return saved;
    }

    @Auditable(action = "ADMIN_SUBSCRIPTION_RENEWED", entityType = "Subscription")
    @Transactional
    public SubscriptionResponseDTO renewSubscription(String userId, AdminRenewSubscriptionRequestDTO request) {
        User user = findById(userId);

        Plan userPlan = user.getPlan();
        if (userPlan == null) {
            throw new BadRequestException("Usuário sem plano definido");
        }

        if (request != null && request.getPlan() != null && request.getPlan() != userPlan) {
            throw new BadRequestException("Para trocar plano, use a ação de Trocar plano antes de renovar a assinatura");
        }

        if (userPlan == Plan.FREE) {
            throw new BadRequestException("Não é possível renovar assinatura no plano FREE");
        }

        int days = request != null && request.getDays() != null ? request.getDays().intValue() : 30;
        if (days < 1 || days > 3650) {
            throw new BadRequestException("days deve estar entre 1 e 3650");
        }

        LocalDate today = LocalDate.now();

        Subscription subscription = subscriptionRepository.findByUser(user).orElseGet(Subscription::new);

        LocalDate currentEnd = subscription.getEndDate();
        LocalDate base = (currentEnd != null && !currentEnd.isBefore(today)) ? currentEnd : today;
        LocalDate newEnd = base.plusDays(days);

        subscription.setUser(user);
        subscription.setPlan(userPlan);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(false);

        if (subscription.getStartDate() == null || (currentEnd != null && currentEnd.isBefore(today))) {
            subscription.setStartDate(today);
        }

        subscription.setEndDate(newEnd);

        Subscription saved = subscriptionRepository.save(subscription);
        return toSubscriptionDTO(saved);
    }

    @Auditable(action = "ADMIN_PAYMENT_CREATED", entityType = "Payment")
    @Transactional
    public PaymentResponseDTO createPayment(String userId, AdminCreateUserPaymentRequestDTO request) {
        User user = findById(userId);
        if (request == null) {
            throw new BadRequestException("Dados do pagamento são obrigatórios");
        }
        if (request.getPlan() == null) {
            throw new BadRequestException("Plano é obrigatório");
        }

        Payment payment = new Payment();
        payment.setUser(user);
        payment.setPlan(request.getPlan());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus(PaymentStatus.APPROVED);

        PaymentProvider provider = request.getProvider() != null ? request.getProvider() : PaymentProvider.INTERNAL;
        payment.setProvider(provider);

        String providerPaymentId = request.getProviderPaymentId();
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            providerPaymentId = "MANUAL-" + System.currentTimeMillis();
        }

        payment.setProviderPaymentId(providerPaymentId);
        payment.setProviderRawStatus(request.getProviderRawStatus() != null ? request.getProviderRawStatus() : "approved");

        LocalDateTime now = LocalDateTime.now();
        payment.setCreatedAt(now);
        payment.setPaidAt(request.getPaidAt() != null ? request.getPaidAt() : now);

        Payment saved = paymentRepository.save(payment);
        return toPaymentDTO(saved);
    }

    private void syncExistingSubscriptionAfterPlanChange(Subscription subscription, Plan newPlan) {
        if (newPlan == Plan.FREE) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            subscription.setAutoRenew(false);
            subscription.setEndDate(LocalDate.now());
            subscriptionRepository.save(subscription);
            return;
        }

        // Mantém as datas (não é renovação), mas alinha o plano se já existe assinatura.
        subscription.setPlan(newPlan);
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            // Não reativa automaticamente; reativação/renovação é uma ação separada.
            subscriptionRepository.save(subscription);
            return;
        }

        subscriptionRepository.save(subscription);
    }

    private SubscriptionResponseDTO toSubscriptionDTO(Subscription subscription) {
        SubscriptionResponseDTO dto = new SubscriptionResponseDTO();
        dto.setId(subscription.getId() != null ? subscription.getId().toString() : null);
        dto.setUserId(subscription.getUser() != null ? subscription.getUser().getId().toString() : null);
        dto.setUserName(subscription.getUser() != null ? subscription.getUser().getName() : null);
        dto.setPlan(subscription.getPlan());
        dto.setStatus(subscription.getStatus());
        dto.setStartDate(subscription.getStartDate());
        dto.setEndDate(subscription.getEndDate());
        dto.setAutoRenew(subscription.isAutoRenew());
        return dto;
    }

    private PaymentResponseDTO toPaymentDTO(Payment payment) {
        PaymentResponseDTO dto = new PaymentResponseDTO();
        dto.setId(payment.getId() != null ? payment.getId().toString() : null);
        dto.setUserId(payment.getUser() != null ? payment.getUser().getId().toString() : null);
        dto.setUserName(payment.getUser() != null ? payment.getUser().getName() : null);
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
