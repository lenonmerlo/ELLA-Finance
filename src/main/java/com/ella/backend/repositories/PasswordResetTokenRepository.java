package com.ella.backend.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ella.backend.entities.PasswordResetToken;
import com.ella.backend.entities.User;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByPerson(User person);

    void deleteByPerson(User person);
}
