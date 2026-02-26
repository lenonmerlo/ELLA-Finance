package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ella.backend.email.EmailService;
import com.ella.backend.entities.PasswordResetToken;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.repositories.PasswordResetTokenRepository;
import com.ella.backend.repositories.UserRepository;
import com.ella.backend.security.JwtService;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private JwtService jwtService() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "resetSecret", "");
        ReflectionTestUtils.setField(jwtService, "expirationMillis", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMillis", 604_800_000L);
        return jwtService;
    }

    @Test
    void resetPassword_happyPath_marksUsedAndUpdatesPassword() throws Exception {
        JwtService jwtService = jwtService();
        PasswordResetService passwordResetService = new PasswordResetService(
            userRepository,
            tokenRepository,
            jwtService,
            passwordEncoder,
            emailService
        );
        ReflectionTestUtils.setField(passwordResetService, "emailEnabled", false);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@exemplo.com");
        user.setName("User");

        String jti = UUID.randomUUID().toString();
        String jwt = jwtService.generatePasswordResetToken(user.getId().toString(), jti, 15);
        String jtiHash = sha256Hex(jti);

        PasswordResetToken dbToken = new PasswordResetToken(jtiHash, user);
        dbToken.setToken(jtiHash);
        dbToken.setPerson(user);
        dbToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        dbToken.setUsedAt(null);

        when(tokenRepository.findByToken(jtiHash)).thenReturn(Optional.of(dbToken));
        when(passwordEncoder.encode("novaSenha")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        passwordResetService.resetPassword(jwt, "novaSenha");

        verify(userRepository).save(any(User.class));
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        assertNotNull(captor.getValue().getUsedAt());

        verify(emailService, never()).send(any());
    }

    @Test
    void resetPassword_expiredToken_returnsGenericBadRequest() {
        JwtService jwtService = jwtService();
        PasswordResetService passwordResetService = new PasswordResetService(
            userRepository,
            tokenRepository,
            jwtService,
            passwordEncoder,
            emailService
        );
        ReflectionTestUtils.setField(passwordResetService, "emailEnabled", false);

        String jwt = jwtService.generatePasswordResetToken(UUID.randomUUID().toString(), UUID.randomUUID().toString(), -1);

        assertThrows(BadRequestException.class, () -> passwordResetService.resetPassword(jwt, "x"));
        verify(tokenRepository, never()).findByToken(any());
    }

    @Test
    void resetPassword_usedToken_returnsGenericBadRequest() throws Exception {
        JwtService jwtService = jwtService();
        PasswordResetService passwordResetService = new PasswordResetService(
            userRepository,
            tokenRepository,
            jwtService,
            passwordEncoder,
            emailService
        );
        ReflectionTestUtils.setField(passwordResetService, "emailEnabled", false);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@exemplo.com");

        String jti = UUID.randomUUID().toString();
        String jwt = jwtService.generatePasswordResetToken(user.getId().toString(), jti, 15);
        String jtiHash = sha256Hex(jti);

        PasswordResetToken dbToken = new PasswordResetToken(jtiHash, user);
        dbToken.setToken(jtiHash);
        dbToken.setPerson(user);
        dbToken.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        dbToken.setUsedAt(LocalDateTime.now());

        when(tokenRepository.findByToken(jtiHash)).thenReturn(Optional.of(dbToken));

        assertThrows(BadRequestException.class, () -> passwordResetService.resetPassword(jwt, "novaSenha"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_wrongPurpose_returnsGenericBadRequest() {
        JwtService jwtService = jwtService();
        PasswordResetService passwordResetService = new PasswordResetService(
            userRepository,
            tokenRepository,
            jwtService,
            passwordEncoder,
            emailService
        );
        ReflectionTestUtils.setField(passwordResetService, "emailEnabled", false);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        // Access token (no purpose=pwd_reset)
        String accessToken = jwtService.generateToken(user);

        assertThrows(BadRequestException.class, () -> passwordResetService.resetPassword(accessToken, "novaSenha"));
        verify(tokenRepository, never()).findByToken(any());
    }

    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        char[] hex = new char[hash.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }
}
