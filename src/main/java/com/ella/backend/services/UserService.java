package com.ella.backend.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ella.backend.audit.Auditable;
import com.ella.backend.email.events.LgpdConsentEmailRequestedEvent;
import com.ella.backend.email.events.UserRegisteredEvent;
import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.enums.Status;
import com.ella.backend.exceptions.BadRequestException;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher publisher;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(String id) {
        UUID uuid = UUID.fromString(id);
        return userRepository.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado com este e-mail"));
    }

    @Auditable(action = "USER_CREATED", entityType = "User")
    public User create(User user) {
        normalizeUser(user);
        validateUserBusinessRules(user, true);

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("E-mail já cadastrado");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User created = userRepository.save(user);

        // 🔔 Eventos de comunicação (async)
        publisher.publishEvent(new UserRegisteredEvent(
                created.getId(),
                created.getName(),
                created.getEmail()
        ));

        publisher.publishEvent(new LgpdConsentEmailRequestedEvent(
                created.getId(),
                created.getName(),
                created.getEmail()
        ));

        return created;
    }


    @Auditable(action = "USER_UPDATED", entityType = "User")
    public User update(String id, User data) {
        User existing = findById(id);

        // Se o e-mail mudou, checa se já existe outro usuário com esse email
        if (!existing.getEmail().equals(data.getEmail())
                && userRepository.existsByEmail(data.getEmail())) {
            throw new RuntimeException("E-mail já cadastrado");
        }

        // Campos de User
        existing.setEmail(data.getEmail());
        if (data.getRole() != null) {
            existing.setRole(data.getRole());
        }

        // Campos herdados de Person (mas o Lombok gera os getters/setters normalmente)
        existing.setName(data.getName());
        existing.setPhone(data.getPhone());
        existing.setBirthDate(data.getBirthDate());
        existing.setAddress(data.getAddress());
        existing.setIncome(data.getIncome());
        existing.setStatus(data.getStatus());
        existing.setLanguage(data.getLanguage());
        existing.setCurrency(data.getCurrency());
        existing.setPlan(data.getPlan());

        // Se vier senha nova, atualiza e encripta.
        // IMPORTANTE: não re-encodar a senha já criptografada (isso quebra o login).
        String incomingPassword = data.getPassword();
        if (incomingPassword != null && !incomingPassword.isBlank()
                && (existing.getPassword() == null || !incomingPassword.equals(existing.getPassword()))) {
            existing.setPassword(passwordEncoder.encode(incomingPassword));
        }

        validateUserBusinessRules(existing, false);

        return userRepository.save(existing);
    }

    @Auditable(action = "USER_AVATAR_UPDATED", entityType = "User")
    public User updateAvatar(String id, byte[] avatar, String contentType) {
        User existing = findById(id);
        existing.setAvatar(avatar);
        existing.setAvatarContentType(contentType);
        return userRepository.save(existing);
    }

    @Auditable(action = "USER_PASSWORD_UPDATED", entityType = "User")
    public void updatePassword(String id, String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BadRequestException("Senha é obrigatória");
        }

        User existing = findById(id);
        existing.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(existing);
    }

    @Auditable(action = "USER_DELETED", entityType = "User")
    public void delete(String id) {
        User existing = findById(id);
        userRepository.delete(existing);
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        return email.trim().toLowerCase();
    }

    private void normalizeUser(User user) {
        user.setEmail(normalizeEmail(user.getEmail()));

        if (user.getStatus() == null) {
            user.setStatus(Status.ACTIVE);
        }

        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
    }

    private void validateUserBusinessRules(User user, boolean isCreate) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("E-mail é obrigatório");
        }

        if (isCreate && (user.getPassword() == null || user.getPassword().isBlank())) {
            throw new BadRequestException("Senha é obrigatória");
        }

        // Regras herdadas de Person (mesmas de PersonService)
        if (user.getName() == null || user.getName().isBlank()) {
            throw new BadRequestException("Nome é obrigatório");
        }

        if (user.getBirthDate() != null && user.getBirthDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("Data de nascimento não pode ser futura");
        }

        if (user.getIncome() != null && user.getIncome().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Renda não pode ser negativa");
        }
    }
}
