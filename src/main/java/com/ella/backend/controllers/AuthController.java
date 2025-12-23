package com.ella.backend.controllers;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ella.backend.dto.ApiResponse;
import com.ella.backend.dto.AuthRequestDTO;
import com.ella.backend.dto.AuthResponseDTO;
import com.ella.backend.dto.DevResetPasswordRequestDTO;
import com.ella.backend.dto.UserResponseDTO;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.mappers.UserMapper;
import com.ella.backend.security.JwtService;
import com.ella.backend.services.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.dev.reset-password-token:}")
    private String devResetPasswordToken;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@RequestBody AuthRequestDTO request, HttpServletResponse response) {

        User user = userService.findByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Credenciais inválidas");
        }

        String token = jwtService.generateToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        AuthResponseDTO authResponse = AuthResponseDTO.builder()
            .token(token)
            .tokenType("Bearer")
            .expiresIn(jwtService.getExpirationMillis())
            .build();

        // set refresh token as HttpOnly cookie
        Cookie cookie = new Cookie("refresh", refresh);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) (jwtService.getRefreshExpirationMillis() / 1000));
        response.addCookie(cookie);

        ApiResponse<AuthResponseDTO> body = ApiResponse.<AuthResponseDTO>builder()
                .success(true)
                .data(authResponse)
                .message("Login realizado com sucesso")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> refresh(HttpServletRequest request, HttpServletResponse response) {
        // read refresh cookie
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("refresh".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new BadRequestException("Refresh token ausente");
        }

        // validate and extract subject
        String email = null;
        try {
            email = jwtService.extractUsername(refreshToken);
            // ensure it's a refresh token
            String type = jwtService.extractClaim(refreshToken, claims -> claims.get("type", String.class));
            if (!"refresh".equals(type)) {
                throw new BadRequestException("Refresh token inválido");
            }
        } catch (Exception e) {
            throw new BadRequestException("Refresh token inválido ou expirado");
        }

        User user = userService.findByEmail(email);
        String newAccess = jwtService.generateToken(user);
        String newRefresh = jwtService.generateRefreshToken(user);

        // set new refresh cookie
        Cookie newCookie = new Cookie("refresh", newRefresh);
        newCookie.setHttpOnly(true);
        newCookie.setPath("/api/auth");
        newCookie.setMaxAge((int) (jwtService.getRefreshExpirationMillis() / 1000));
        response.addCookie(newCookie);

        AuthResponseDTO authResponse = AuthResponseDTO.builder()
                .token(newAccess)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMillis())
                .build();

        ApiResponse<AuthResponseDTO> body = ApiResponse.<AuthResponseDTO>builder()
                .success(true)
                .data(authResponse)
                .message("Token renovado")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(true)
                .message("Logout efetuado")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<UserResponseDTO>builder()
                            .success(false)
                            .message("Usuário não autenticado")
                            .timestamp(java.time.LocalDateTime.now())
                            .build());
        }

        String email = auth.getName();
        var user = userService.findByEmail(email);
        UserResponseDTO dto = UserMapper.toResponseDTO(user);

        ApiResponse<UserResponseDTO> body = ApiResponse.<UserResponseDTO>builder()
                .success(true)
                .data(dto)
                .message("Perfil carregado")
                .timestamp(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.ok(body);
    }

    /**
     * Endpoint apenas para desenvolvimento local.
     * Permite recuperar acesso caso a senha tenha sido re-criptografada indevidamente.
     * Protegido por token estático em `app.dev.reset-password-token`.
     */
    @PostMapping("/dev/reset-password")
    public ResponseEntity<ApiResponse<Void>> devResetPassword(
            @RequestHeader(value = "X-Dev-Reset-Token", required = false) String token,
            @RequestBody DevResetPasswordRequestDTO request
    ) {
        if (devResetPasswordToken == null || devResetPasswordToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Endpoint não habilitado")
                            .timestamp(LocalDateTime.now())
                            .build());
        }

        if (token == null || !devResetPasswordToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Void>builder()
                            .success(false)
                            .message("Token inválido")
                            .timestamp(LocalDateTime.now())
                            .build());
        }

        User user = userService.findByEmail(request.getEmail());
        userService.updatePassword(user.getId().toString(), request.getNewPassword());

        return ResponseEntity.ok(ApiResponse.message("Senha resetada"));
    }
}
