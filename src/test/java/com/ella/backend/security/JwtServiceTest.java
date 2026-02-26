package com.ella.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ella.backend.entities.User;
import com.ella.backend.enums.Role;

class JwtServiceTest {

    @Test
    void generateToken_usesConfiguredAccessExpiration() {
        JwtService jwtService = new JwtService();
        // HS256 requires a sufficiently long key; use 32+ bytes.
        ReflectionTestUtils.setField(jwtService, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "expirationMillis", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMillis", 604_800_000L);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(Role.USER);

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        Date issuedAt = jwtService.extractClaim(token, c -> c.getIssuedAt());
        Date expiration = jwtService.extractClaim(token, c -> c.getExpiration());

        long deltaMs = expiration.getTime() - issuedAt.getTime();
        assertTrue(Math.abs(deltaMs - 3_600_000L) < 2_000L, "access token expiration should be ~1h");
    }

    @Test
    void generateRefreshToken_setsTypeClaimAndUsesConfiguredRefreshExpiration() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "expirationMillis", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMillis", 604_800_000L);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setRole(Role.USER);

        String refresh = jwtService.generateRefreshToken(user);
        assertNotNull(refresh);

        String type = jwtService.extractClaim(refresh, c -> c.get("type", String.class));
        assertEquals("refresh", type);

        Date issuedAt = jwtService.extractClaim(refresh, c -> c.getIssuedAt());
        Date expiration = jwtService.extractClaim(refresh, c -> c.getExpiration());

        long deltaMs = expiration.getTime() - issuedAt.getTime();
        assertTrue(Math.abs(deltaMs - 604_800_000L) < 2_000L, "refresh token expiration should be ~7d");
    }

    @Test
    void generatePasswordResetToken_setsPurposeAndJti_andUsesUserIdAsSubject() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(jwtService, "resetSecret", "");
        ReflectionTestUtils.setField(jwtService, "expirationMillis", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpirationMillis", 604_800_000L);

        String userId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        String token = jwtService.generatePasswordResetToken(userId, jti, 15);
        assertNotNull(token);

        JwtService.PasswordResetPayload payload = jwtService.parseAndValidatePasswordResetToken(token);
        assertEquals(userId, payload.getUserId());
        assertEquals(jti, payload.getJti());

        String purpose = jwtService.extractClaim(token, c -> c.get(JwtService.CLAIM_PURPOSE, String.class));
        assertEquals(JwtService.PURPOSE_PASSWORD_RESET, purpose);
    }
}
