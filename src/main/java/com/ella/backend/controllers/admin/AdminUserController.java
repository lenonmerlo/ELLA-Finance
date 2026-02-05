package com.ella.backend.controllers.admin;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.dto.admin.AdminCreateUserPaymentRequestDTO;
import com.ella.backend.dto.admin.AdminRenewSubscriptionRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserPlanRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserRoleRequestDTO;
import com.ella.backend.dto.admin.AdminUpdateUserStatusRequestDTO;
import com.ella.backend.dto.admin.AdminUserListItemDTO;
import com.ella.backend.dto.payment.PaymentResponseDTO;
import com.ella.backend.dto.payment.SubscriptionResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;
import com.ella.backend.mappers.UserMapper;
import com.ella.backend.services.admin.AdminUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserListItemDTO>>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AdminUserListItemDTO> response = adminUserService.search(q, role, status, page, size)
                .map(user -> {
                    AdminUserListItemDTO dto = new AdminUserListItemDTO();
                    dto.setId(user.getId().toString());
                    dto.setName(user.getName());
                    dto.setEmail(user.getEmail());
                    dto.setPlan(user.getPlan());
                    dto.setRole(user.getRole());
                    dto.setStatus(user.getStatus());
                    dto.setCreatedAt(user.getCreatedAt());
                    dto.setUpdatedAt(user.getUpdatedAt());
                    return dto;
                });

        return ResponseEntity.ok(ApiResponse.success(response, "Usuários carregados com sucesso"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getById(@PathVariable String id) {
        User user = adminUserService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(UserMapper.toResponseDTO(user), "Usuário encontrado"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserStatusRequestDTO request
    ) {
        User updated = adminUserService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(UserMapper.toResponseDTO(updated), "Status atualizado com sucesso"));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateRole(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserRoleRequestDTO request
    ) {
        User updated = adminUserService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success(UserMapper.toResponseDTO(updated), "Role atualizada com sucesso"));
    }

    @PutMapping("/{id}/plan")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updatePlan(
            @PathVariable String id,
            @Valid @RequestBody AdminUpdateUserPlanRequestDTO request
    ) {
        User updated = adminUserService.updatePlan(id, request);
        return ResponseEntity.ok(ApiResponse.success(UserMapper.toResponseDTO(updated), "Plano atualizado com sucesso"));
    }

    @PostMapping("/{id}/subscription/renew")
    public ResponseEntity<ApiResponse<SubscriptionResponseDTO>> renewSubscription(
            @PathVariable String id,
            @Valid @RequestBody AdminRenewSubscriptionRequestDTO request
    ) {
        SubscriptionResponseDTO updated = adminUserService.renewSubscription(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Assinatura renovada com sucesso"));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<PaymentResponseDTO>> createPayment(
            @PathVariable String id,
            @Valid @RequestBody AdminCreateUserPaymentRequestDTO request
    ) {
        PaymentResponseDTO created = adminUserService.createPayment(id, request);
        return ResponseEntity.ok(ApiResponse.success(created, "Pagamento registrado com sucesso"));
    }
}
