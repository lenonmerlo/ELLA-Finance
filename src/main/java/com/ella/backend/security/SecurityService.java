package com.ella.backend.security;

import com.ella.backend.entities.User;
import com.ella.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityService {

    private final UserService userService;

    /**
     * Verifica se o usuário autenticado é dono do User ou Person de id informado.
     * Vamos considerar que o id pode ser:
     * - id do User
     * - ou id da Person associada
     */
    public boolean isCurrentUser(String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        String email = auth.getName(); // subject do JWT (username)
        User user = userService.findByEmail(email);

        return user.getId().equals(id);

    }
}
