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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

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
}
