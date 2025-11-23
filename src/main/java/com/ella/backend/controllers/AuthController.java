package com.ella.backend.controllers;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.AuthRequestDTO;
import com.ella.backend.dto.AuthResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.services.UserService;
import com.ella.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@RequestBody AuthRequestDTO request) {

        User user = userService.findByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Credenciais inválidas");
        }

        String token = jwtService.generateToken(user);

        AuthResponseDTO authResponse = AuthResponseDTO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMillis())
                .build();

        ApiResponse<AuthResponseDTO> body = ApiResponse.<AuthResponseDTO>builder()
                .success(true)
                .data(authResponse)
                .message("Login realizado com sucesso")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(body);
    }
}
