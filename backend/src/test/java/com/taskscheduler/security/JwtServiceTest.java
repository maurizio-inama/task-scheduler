package com.taskscheduler.security;

import com.taskscheduler.domain.entity.Role;
import com.taskscheduler.domain.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET =
            "unit-test-secret-key-that-is-long-enough-for-hs256-signing-1234567890";

    private static final String OTHER_SECRET =
            "unit-test-other-secret-key-that-is-also-long-enough-00000000000000";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600);
    }

    private User user() {
        return new User(
                "alice",
                "encoded-hash",
                "Alice",
                "Smith",
                "alice@example.com",
                Role.OPERATOR,
                true
        );
    }

    @Test
    void shouldGenerateTokenContainingIdentityAndRole() {
        String token = jwtService.generateToken(user());

        assertTrue(jwtService.isValid(token));
        assertEquals("alice", jwtService.extractUsername(token));
        assertEquals("OPERATOR", jwtService.extractRole(token));
    }

    @Test
    void shouldExposeConfiguredExpiration() {
        assertEquals(3600, jwtService.getExpirationSeconds());
    }

    @Test
    void shouldRejectMalformedToken() {
        assertFalse(jwtService.isValid("not-a-jwt"));
        assertFalse(jwtService.isValid("a.b.c"));
        assertFalse(jwtService.isValid(""));
        assertFalse(jwtService.isValid(null));
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "OPERATOR")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void shouldRejectExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "OPERATOR")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void shouldReturnNullRoleWhenClaimIsMissing() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        String token = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertNull(jwtService.extractRole(token));
    }
}