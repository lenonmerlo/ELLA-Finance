package com.ella.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.ella.backend.entities.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    // Optional dedicated secret for password reset tokens.
    // If blank, fall back to jwt.secret.
    @Value("${jwt.reset.secret:}")
    private String resetSecret;

    @Getter
    @Value("${jwt.expiration}")
    private Long expirationMillis;
    
    @Getter
    @Value("${jwt.refreshExpiration:604800000}")
    private Long refreshExpirationMillis;

    public static final String CLAIM_PURPOSE = "purpose";
    public static final String PURPOSE_PASSWORD_RESET = "pwd_reset";

    public String generateToken(User user) {
        return generateToken(Map.of(
                "id", user.getId().toString(),
                "role", user.getRole().name()
        ), user.getEmail());
    }

    public String generateRefreshToken(User user) {
        return generateRefreshToken(Map.of(
                "id", user.getId().toString(),
                "role", user.getRole().name(),
                "type", "refresh"
        ), user.getEmail());
    }

    /**
     * Generates a short-lived JWT used exclusively for password reset.
     *
     * Claims:
     * - sub: userId
     * - purpose: pwd_reset
     * - jti: provided id
     */
    public String generatePasswordResetToken(String userId, String jti, long expirationMinutes) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (expirationMinutes * 60_000L));

        return Jwts.builder()
                .setSubject(userId)
                .setId(jti)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .claim(CLAIM_PURPOSE, PURPOSE_PASSWORD_RESET)
                .signWith(getResetSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public PasswordResetPayload parseAndValidatePasswordResetToken(String token) {
        Claims claims = extractAllClaimsWithResetKey(token);

        String purpose = claims.get(CLAIM_PURPOSE, String.class);
        if (!PURPOSE_PASSWORD_RESET.equals(purpose)) {
            throw new IllegalArgumentException("Token inválido");
        }

        String userId = claims.getSubject();
        String jti = claims.getId();
        if (userId == null || userId.isBlank() || jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("Token inválido");
        }

        return new PasswordResetPayload(userId, jti, claims.getExpiration());
    }


    private String generateToken(Map<String, Object> extraClaims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String generateRefreshToken(Map<String, Object> extraClaims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMillis);

        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Claims extractAllClaimsWithResetKey(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getResetSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        // usa o secret como string normal (não Base64)
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private Key getResetSigningKey() {
        if (resetSecret == null || resetSecret.isBlank()) {
            return getSigningKey();
        }
        byte[] keyBytes = resetSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Getter
    public static class PasswordResetPayload {
        private final String userId;
        private final String jti;
        private final Date expiration;

        public PasswordResetPayload(String userId, String jti, Date expiration) {
            this.userId = userId;
            this.jti = jti;
            this.expiration = expiration;
        }
    }
}
