package com.ella.backend.services;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ella.backend.email.EmailMessage;
import com.ella.backend.email.EmailService;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public void forgotPassword(String email) {
        passwordResetService.requestPasswordReset(email);
    }

    public void resetPassword(String token, String newPassword) {
        passwordResetService.resetPassword(token, newPassword);
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

