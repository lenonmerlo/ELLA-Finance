// Rota base: /api/users
package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.UserRequestDTO;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.mappers.UserMapper;
import com.ella.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserResponseDTO>> findById(@PathVariable String id) {
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
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(
            @PathVariable String id,
            @Valid @RequestBody UserRequestDTO request
    ) {
        User entity = UserMapper.toEntity(request);
        User updated = userService.update(id, entity);
        UserResponseDTO dto = UserMapper.toResponseDTO(updated);

        return ResponseEntity.ok(
                ApiResponse.success(dto, "Usuário atualizado com sucesso")
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        userService.delete(id);

        return ResponseEntity.ok(
                ApiResponse.message("Usuário deletado com sucesso")
        );
    }
}
