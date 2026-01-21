// Rota base: /api/users
package com.ella.backend.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.UpdateProfileRequestDTO;
import com.ella.backend.dto.UpdateUserRoleRequestDTO;
import com.ella.backend.dto.UserRequestDTO;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.mappers.UserMapper;
import com.ella.backend.services.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.success("OK - Ella Backend", "Health check")
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> listAll() {
        List<User> users = userService.findAll();
        List<UserResponseDTO> dtos = users.stream()
                .map(UserMapper::toResponseDTO)
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success(dtos, "Usuários listados com sucesso")
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> findById(@PathVariable String id) {
        // Check permission: ADMIN or self
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin) {
            String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userService.findByEmail(currentEmail);
            if (!currentUser.getId().toString().equals(id)) {
                throw new AccessDeniedException("Você não tem permissão para acessar este usuário");
            }
        }
        
        User user = userService.findById(id);
        UserResponseDTO dto = UserMapper.toResponseDTO(user);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Usuário encontrado")
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDTO>> create(
            @Valid @RequestBody UserRequestDTO request
    ) {
        User entity = UserMapper.toEntity(request);
        User created = userService.create(entity);
        UserResponseDTO dto = UserMapper.toResponseDTO(created);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Usuário criado com sucesso"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateProfileRequestDTO request
    ) {
        // Check permission: ADMIN or self
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin) {
            String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userService.findByEmail(currentEmail);
            if (!currentUser.getId().toString().equals(id)) {
                throw new AccessDeniedException("Você não tem permissão para atualizar este usuário");
            }
        }

        User user = userService.findById(id);
        
        // Update only the provided fields
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress());
        }
        if (request.getBirthDate() != null) {
            user.setBirthDate(request.getBirthDate());
        }
        
        User updated = userService.update(id, user);
        UserResponseDTO dto = UserMapper.toResponseDTO(updated);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Usuário atualizado com sucesso")
        );
    }

    @PostMapping(path = "/{id}/avatar", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<UserResponseDTO>> uploadAvatar(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file
    ) {
        // Check permission: ADMIN or self
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userService.findByEmail(currentEmail);
            if (!currentUser.getId().toString().equals(id)) {
                throw new AccessDeniedException("Você não tem permissão para atualizar este usuário");
            }
        }

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Arquivo de avatar é obrigatório");
        }

        // 5MB (como descrito na UI)
        long maxBytes = 5L * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Avatar deve ter no máximo 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BadRequestException("Formato de avatar inválido (use imagem)");
        }

        try {
            User updated = userService.updateAvatar(id, file.getBytes(), contentType);
            UserResponseDTO dto = UserMapper.toResponseDTO(updated);
            return ResponseEntity.ok(ApiResponse.success(dto, "Avatar atualizado com sucesso"));
        } catch (Exception e) {
            throw new BadRequestException("Falha ao processar avatar");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        // Check permission: ADMIN or self
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin) {
            String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            User currentUser = userService.findByEmail(currentEmail);
            if (!currentUser.getId().toString().equals(id)) {
                throw new AccessDeniedException("Você não tem permissão para deletar este usuário");
            }
        }
        
        userService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.message("Usuário deletado com sucesso")
        );
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateRole(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRoleRequestDTO request
    ) {
        User updated = userService.updateRole(id, request.getRole());
        UserResponseDTO dto = UserMapper.toResponseDTO(updated);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Role atualizado com sucesso")
        );
    }
}
