package com.ella.backend.services;

import com.ella.backend.entities.User;
import com.ella.backend.exceptions.ConflictException;
import com.ella.backend.exceptions.ResourceNotFoundException;
import com.ella.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

    public User create(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("E-mail já cadastrado");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User update(String id, User data) {
        User existing = findById(id);

        // Se o e-mail mudou, checa se já existe outro usuário com esse email
        if (!existing.getEmail().equals(data.getEmail())
                && userRepository.existsByEmail(data.getEmail())) {
            throw new RuntimeException("E-mail já cadastrado");
        }

        // Campos de User
        existing.setEmail(data.getEmail());
        existing.setRole(data.getRole());

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

        // Se vier senha nova, atualiza e encripta. Se vier null / vazio, mantém a antiga.
        if (data.getPassword() != null && !data.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(data.getPassword()));
        }

        return userRepository.save(existing);
    }

    public void delete(String id) {
        User existing = findById(id);
        userRepository.delete(existing);
    }
}
