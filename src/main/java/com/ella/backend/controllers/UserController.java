package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.UserRequestDTO;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.mappers.UserMapper;
import com.ella.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/health")
    public String health() {
        return "OK - Ella Backend";
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponseDTO>>> listAll() {
        List<User> users = userService.findAll();
        List<UserResponseDTO> dtos = users.stream()
                .map(UserMapper::toResponseDTO)
                .toList();

        ApiResponse<List<UserResponseDTO>> body = ApiResponse.<List<UserResponseDTO>>builder()
                .success(true)
                .data(dtos)
                .message("Usuários listados com sucesso")
                .timestamp(LocalDateTime.from(Instant.now()))
                .build();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserResponseDTO>> findById(@PathVariable String id) {
        User user = userService.findById(id);
        UserResponseDTO dto = UserMapper.toResponseDTO(user);

        ApiResponse<UserResponseDTO> body = ApiResponse.<UserResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Usuário encontrado")
                .timestamp(LocalDateTime.from(Instant.now()))
                .build();

        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDTO>> create(@RequestBody UserRequestDTO request) {
        User entity = UserMapper.toEntity(request);
        User created = userService.create(entity);
        UserResponseDTO dto = UserMapper.toResponseDTO(created);

        ApiResponse<UserResponseDTO> body = ApiResponse.<UserResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Usuário criado com sucesso")
                .timestamp(LocalDateTime.from(Instant.now()))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isCurrentUser(#id)")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(@PathVariable String id,
                                                               @RequestBody UserRequestDTO request) {
        User entity = UserMapper.toEntity(request);
        User updated = userService.update(id, entity);
        UserResponseDTO dto = UserMapper.toResponseDTO(updated);

        ApiResponse<UserResponseDTO> body = ApiResponse.<UserResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Usuário atualizado com sucesso")
                .timestamp(LocalDateTime.from(Instant.now()))
                .build();

        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        userService.delete(id);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(true)
                .data(null)
                .message("Usuário deletado com sucesso")
                .timestamp(LocalDateTime.from(Instant.now()))
                .build();

        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(body);
    }
}
