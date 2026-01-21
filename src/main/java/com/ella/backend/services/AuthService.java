package com.ella.backend.services;

import java.time.LocalDateTime;
import java.util.Map;
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
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PasswordResetTokenRepository;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl = "http://localhost:5173";

    @Value("${app.frontend.reset-password-path:/auth/reset-password}")
    private String resetPasswordPath = "/auth/reset-password";

    public void forgotPassword(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("E-mail é obrigatório");
        }

        // Segurança: não revelar se o e-mail existe ou não.
        var userOpt = userRepository.findByEmail(normalized);
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = tokenRepository.findByPerson(user)
            .orElseGet(() -> new PasswordResetToken(token, user));

        resetToken.setToken(token);
        resetToken.setPerson(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        tokenRepository.save(resetToken);

        String baseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
            ? "http://localhost:5173"
            : frontendBaseUrl;
        String resetPath = (resetPasswordPath == null || resetPasswordPath.isBlank())
            ? "/auth/reset-password"
            : resetPasswordPath;

        String resetLink = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path(resetPath)
            .queryParam("token", token)
            .build()
            .toUriString();

        EmailMessage message = EmailMessage.builder()
                .to(normalized)
                .subject("Redefinir sua senha")
                .templateName("forgot-password")
                .variables(Map.of("resetLink", resetLink))
                .build();

        // Não quebrar o fluxo se o provedor de e-mail estiver em modo de testes
        // ou momentaneamente indisponível.
        try {
            emailService.send(message);
        } catch (Exception e) {
            log.warn("Falha ao enviar email de reset de senha to={}. Link (DEV): {}", normalized, resetLink, e);
        }
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Token inválido");
        }

        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("Senha é obrigatória");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Token inválido"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token expirado");
        }

        User user = resetToken.getPerson();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(resetToken);

        EmailMessage message = EmailMessage.builder()
                .to(user.getEmail())
                .subject("Senha redefinida com sucesso")
                .templateName("reset-password-success")
                .variables(Map.of("name", user.getName()))
                .build();

        try {
            emailService.send(message);
        } catch (Exception e) {
            log.warn("Falha ao enviar email de confirmação de reset de senha to={}", user.getEmail(), e);
        }
    }

    public User register(String name, String email, String password) {
        String normalized = normalizeEmail(email);

        if (name == null || name.isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }

        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("E-mail é obrigatório");
        }

        if (password == null || password.isBlank()) {
            throw new BadRequestException("Senha é obrigatória");
        }

        if (userRepository.existsByEmail(normalized)) {
            throw new ConflictException("E-mail já cadastrado");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(normalized);
        user.setPassword(passwordEncoder.encode(password));

        User created = userRepository.save(user);

        EmailMessage message = EmailMessage.builder()
                .to(normalized)
                .subject("Bem-vindo ao ELLA!")
                .templateName("register-welcome")
                .variables(Map.of("name", name))
                .build();

        emailService.send(message);

        return created;
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }
}
