package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ella.backend.email.EmailMessage;
import com.ella.backend.email.EmailService;
import com.ella.backend.entities.PasswordResetToken;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.PasswordResetTokenRepository;
import com.ella.backend.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void forgotPassword_userExists_savesTokenAndSendsEmail() {
        User user = new User();
        user.setName("Maria");
        user.setEmail("maria@exemplo.com");

        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByPerson(user)).thenReturn(Optional.empty());
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.forgotPassword("maria@exemplo.com");

        verify(tokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());

        EmailMessage message = captor.getValue();
        assertEquals("maria@exemplo.com", message.getTo());
        assertEquals("Redefinir sua senha", message.getSubject());
        assertEquals("forgot-password", message.getTemplateName());

        Object resetLink = message.getVariables().get("resetLink");
        assertNotNull(resetLink);
        assertTrue(resetLink.toString().contains("/auth/reset-password?token="));
    }

    @Test
    void forgotPassword_existingToken_updatesAndSendsEmail() {
        User user = new User();
        user.setName("Maria");
        user.setEmail("maria@exemplo.com");

        PasswordResetToken existing = new PasswordResetToken("old", user);
        existing.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findByEmail("maria@exemplo.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByPerson(user)).thenReturn(Optional.of(existing));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.forgotPassword("maria@exemplo.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();

        assertEquals(user, saved.getPerson());
        assertNotNull(saved.getToken());
        assertTrue(saved.getToken().length() > 10);
        assertTrue(saved.getExpiryDate().isAfter(LocalDateTime.now()));

        verify(emailService).send(any(EmailMessage.class));
    }

    @Test
    void forgotPassword_userNotFound_throwsNotFound() {
        when(userRepository.findByEmail("x@exemplo.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.forgotPassword("x@exemplo.com"));
        verify(emailService, never()).send(any());
    }

    @Test
    void resetPassword_invalidToken_throwsBadRequest() {
        when(tokenRepository.findByToken("bad")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.resetPassword("bad", "nova"));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void resetPassword_expiredToken_throwsBadRequest() {
        User user = new User();
        user.setName("João");
        user.setEmail("joao@exemplo.com");

        PasswordResetToken resetToken = new PasswordResetToken("t1", user);
        resetToken.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByToken("t1")).thenReturn(Optional.of(resetToken));

        assertThrows(BadRequestException.class, () -> authService.resetPassword("t1", "nova"));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void resetPassword_success_updatesPassword_deletesToken_andSendsEmail() {
        User user = new User();
        user.setName("Ana");
        user.setEmail("ana@exemplo.com");

        PasswordResetToken resetToken = new PasswordResetToken("t2", user);
        resetToken.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByToken("t2")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("novaSenha")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resetPassword("t2", "novaSenha");

        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPassword())));
        verify(tokenRepository).delete(resetToken);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());

        EmailMessage message = captor.getValue();
        assertEquals("ana@exemplo.com", message.getTo());
        assertEquals("reset-password-success", message.getTemplateName());
        assertEquals("Senha redefinida com sucesso", message.getSubject());
        assertEquals("Ana", message.getVariables().get("name"));
    }

    @Test
    void register_emailAlreadyExists_throwsConflict() {
        when(userRepository.existsByEmail("test@exemplo.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> authService.register("Teste", "test@exemplo.com", "123"));
        verify(userRepository, never()).save(any());
        verify(emailService, never()).send(any());
    }

    @Test
    void register_success_savesUser_andSendsEmail() {
        when(userRepository.existsByEmail("novo@exemplo.com")).thenReturn(false);
        when(passwordEncoder.encode("123"))
                .thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = authService.register("Novo", "novo@exemplo.com", "123");

        assertNotNull(created);
        assertEquals("novo@exemplo.com", created.getEmail());
        assertEquals("hashed", created.getPassword());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailService).send(captor.capture());

        EmailMessage message = captor.getValue();
        assertEquals("novo@exemplo.com", message.getTo());
        assertEquals("register-welcome", message.getTemplateName());
        assertEquals("Bem-vindo ao ELLA!", message.getSubject());
        assertEquals("Novo", message.getVariables().get("name"));
    }
}
