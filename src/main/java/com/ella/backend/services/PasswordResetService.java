package com.ella.backend.services;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.ella.backend.email.EmailMessage;
import com.ella.backend.email.EmailService;
import com.ella.backend.entities.PasswordResetToken;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.repositories.PasswordResetTokenRepository;
import com.ella.backend.repositories.UserRepository;
import com.ella.backend.security.JwtService;

import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.frontend.reset-password-path:/reset-password}")
    private String resetPasswordPath;

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    @Value("${auth.password-reset.expiration-minutes:15}")
    private long resetExpirationMinutes;

    public void requestPasswordReset(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            // Keep response generic (controller). Here we just noop.
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty()) {
            // Do not reveal whether the email exists.
            return;
        }

        User user = userOpt.get();

        String jti = UUID.randomUUID().toString();
        String jtiHash = sha256Hex(jti);

        PasswordResetToken resetToken = tokenRepository.findByPerson(user)
                .orElseGet(() -> new PasswordResetToken(jtiHash, user));

        resetToken.setToken(jtiHash);
        resetToken.setPerson(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(resetExpirationMinutes));
        resetToken.setUsedAt(null);
        tokenRepository.save(resetToken);

        String resetJwt = jwtService.generatePasswordResetToken(user.getId().toString(), jti, resetExpirationMinutes);

        String baseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? "http://localhost:5173"
                : frontendBaseUrl;
        String resetPath = (resetPasswordPath == null || resetPasswordPath.isBlank())
                ? "/reset-password"
                : resetPasswordPath;

        String resetLink = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(resetPath)
                .queryParam("token", resetJwt)
                .build()
                .toUriString();

        if (!emailEnabled) {
            log.info("Pedido de reset de senha recebido (email desabilitado). userId={}, email={}",
                    user.getId(), maskEmail(normalized));
            return;
        }

        EmailMessage message = EmailMessage.builder()
                .to(normalized)
                .subject("Redefinir sua senha")
                .templateName("forgot-password")
                .variables(Map.of("resetLink", resetLink))
                .build();

        try {
            emailService.send(message);
        } catch (Exception e) {
            // Do not log token/resetLink.
            log.warn("Falha ao enviar email de reset de senha. userId={}, email={}", user.getId(), maskEmail(normalized), e);
        }
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("Senha é obrigatória");
        }

        JwtService.PasswordResetPayload payload;
        try {
            payload = jwtService.parseAndValidatePasswordResetToken(token);
        } catch (ExpiredJwtException e) {
            throw new BadRequestException("Token inválido ou expirado");
        } catch (Exception e) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        UUID userId;
        try {
            userId = UUID.fromString(payload.getUserId());
        } catch (Exception e) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        String jtiHash = sha256Hex(payload.getJti());

        PasswordResetToken resetToken = tokenRepository.findByToken(jtiHash)
                .orElseThrow(() -> new BadRequestException("Token inválido ou expirado"));

        if (resetToken.getPerson() == null || resetToken.getPerson().getId() == null
                || !resetToken.getPerson().getId().equals(userId)) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        if (resetToken.getUsedAt() != null) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        if (resetToken.getExpiryDate() == null || resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token inválido ou expirado");
        }

        User user = resetToken.getPerson();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);

        if (!emailEnabled) {
            log.info("Senha redefinida (email desabilitado). userId={}, email={}", user.getId(), maskEmail(user.getEmail()));
            return;
        }

        EmailMessage message = EmailMessage.builder()
                .to(user.getEmail())
                .subject("Senha redefinida com sucesso")
                .templateName("reset-password-success")
                .variables(Map.of("name", user.getName()))
                .build();

        try {
            emailService.send(message);
        } catch (Exception e) {
            log.warn("Falha ao enviar email de confirmação de reset de senha. userId={}, email={}",
                    user.getId(), maskEmail(user.getEmail()), e);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.charAt(0) + "***";
        return maskedLocal + "@" + domain;
    }

    private static String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHexLower(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }
}
