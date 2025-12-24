package com.ella.backend.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Should throw ConflictException when trying to create a user with duplicated email")
    void createUser_WithDuplicatedEmail_ShouldThrowException() {
        // Arrange
        String email = "test@example.com";

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword("plain-password");
        newUser.setName("Test User");

        // Mock: email já existe
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Act + Assert
        assertThrows(ConflictException.class, () -> userService.create(newUser));

        // Garantir que NUNCA chegou a salvar
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should encode password before saving a new user")
    void createUser_ShouldEncodePasswordBeforeSaving() {
        // Arrange
        String email = "newuser@example.com";
        String rawPassword = "plain-password";
        String encodedPassword = "$2a$10$encoded.password.hash";

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(rawPassword);
        newUser.setName("New User");

        // Mock: nenhum usuário existente com este e-mail
        when(userRepository.existsByEmail(email)).thenReturn(false);

        // Mock do encoder
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // Mock do save: retorna um User com ID gerado
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID()); // ✅ UUID válido
        savedUser.setEmail(email);
        savedUser.setPassword(encodedPassword);
        savedUser.setName("New User");
        savedUser.setRole(Role.USER);
        savedUser.setStatus(Status.ACTIVE);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = userService.create(newUser);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(encodedPassword, result.getPassword());
        assertEquals(email, result.getEmail());

        // Verifica que o passwordEncoder foi chamado com a senha crua
        verify(passwordEncoder, times(1)).encode(rawPassword);

        // Verifica que o save foi chamado
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when email is blank")
    void createUser_WithBlankEmail_ShouldThrowException() {
        // Arrange
        User newUser = new User();
        newUser.setEmail("");
        newUser.setPassword("valid-password");
        newUser.setName("Test User");

        // Act + Assert
        assertThrows(BadRequestException.class, () -> userService.create(newUser));

        // Garantir que nunca tentou verificar se email existe
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when password is blank on create")
    void createUser_WithBlankPassword_ShouldThrowException() {
        // Arrange
        User newUser = new User();
        newUser.setEmail("test@example.com");
        newUser.setPassword("");
        newUser.setName("Test User");

        // Act + Assert
        assertThrows(BadRequestException.class, () -> userService.create(newUser));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should set default role as USER when not specified")
    void createUser_ShouldSetDefaultRole() {
        // Arrange
        String email = "newuser@example.com";
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword("password123");
        newUser.setName("New User");
        // Não define role

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail(email);
        savedUser.setRole(Role.USER);
        savedUser.setStatus(Status.ACTIVE);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        User result = userService.create(newUser);

        // Assert
        assertEquals(Role.USER, result.getRole());
        assertEquals(Status.ACTIVE, result.getStatus());
    }
}
