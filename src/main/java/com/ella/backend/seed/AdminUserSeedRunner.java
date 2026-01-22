package com.ella.backend.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;
import com.ella.backend.repositories.UserRepository;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class AdminUserSeedRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminUserSeedRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserSeedRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            logger.info("[Seed] Admin user already exists. Skipping.");
            return;
        }

        String email = requiredEnv("APP_SEED_ADMIN_EMAIL");
        String rawPassword = requiredEnv("APP_SEED_ADMIN_PASSWORD");
        String name = requiredEnv("APP_SEED_ADMIN_NAME");

        User user = userRepository.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setName(name);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.ADMIN);

        userRepository.save(user);
        logger.info("[Seed] Admin user ensured: {}", email);
    }

    private String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value.trim();
    }
}
