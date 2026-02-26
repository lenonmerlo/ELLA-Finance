package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ella.backend.email.EmailMessage;
import com.ella.backend.email.EmailService;
import com.ella.backend.entities.User;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

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
