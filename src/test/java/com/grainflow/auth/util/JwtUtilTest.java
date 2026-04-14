package com.grainflow.auth.util;

import com.grainflow.auth.entity.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtil")
class JwtUtilTest {

    // Secret must be >= 256 bits (32 chars) for HMAC-SHA256
    private static final String SECRET   = "test-secret-key-that-is-long-enough-for-hmac";
    private static final long   ACCESS   = 900_000L;   // 15 min
    private static final long   REFRESH  = 604_800_000L; // 7 days

    private JwtUtil jwtUtil;

    private final UUID   userId    = UUID.randomUUID();
    private final UUID   companyId = UUID.randomUUID();
    private final String email     = "alice@grainflow.com";
    private final Role   role      = Role.MANAGER;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS, REFRESH);
    }



    @Test
    @DisplayName("generateAccessToken: should embed all claims")
    void generateAccessToken_shouldEmbedAllClaims() {
        String token = jwtUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
        assertThat(jwtUtil.extractRole(token)).isEqualTo(role.name());
        assertThat(jwtUtil.extractCompanyId(token)).isEqualTo(companyId);
    }

    @Test
    @DisplayName("generateAccessToken: fresh token should not be expired")
    void generateAccessToken_shouldNotBeExpired() {
        String token = jwtUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("generateAccessToken: isTokenValid returns true for correct userId")
    void generateAccessToken_isTokenValid_shouldReturnTrue() {
        String token = jwtUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(jwtUtil.isTokenValid(token, userId)).isTrue();
    }

    @Test
    @DisplayName("generateAccessToken: isTokenValid returns false for wrong userId")
    void generateAccessToken_isTokenValid_shouldReturnFalse_whenWrongUserId() {
        String token = jwtUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(jwtUtil.isTokenValid(token, UUID.randomUUID())).isFalse();
    }


    @Test
    @DisplayName("generateRefreshToken: should only contain subject (userId)")
    void generateRefreshToken_shouldContainUserId() {
        String token = jwtUtil.generateRefreshToken(userId);

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("generateRefreshToken: fresh token should not be expired")
    void generateRefreshToken_shouldNotBeExpired() {
        String token = jwtUtil.generateRefreshToken(userId);

        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }


    @Test
    @DisplayName("isTokenExpired: should return true for immediately expired token")
    void isTokenExpired_shouldReturnTrue_forExpiredToken() {
        // expiration = 0 means the token expires at the same millisecond it was issued
        JwtUtil expiredUtil = new JwtUtil(SECRET, 0L, REFRESH);
        String token = expiredUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(expiredUtil.isTokenExpired(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: should return false for expired token")
    void isTokenValid_shouldReturnFalse_forExpiredToken() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, 0L, REFRESH);
        String token = expiredUtil.generateAccessToken(userId, email, role, companyId);

        assertThat(expiredUtil.isTokenValid(token, userId)).isFalse();
    }


    @Test
    @DisplayName("extractUserId: should throw on tampered token")
    void extractUserId_shouldThrow_onTamperedToken() {
        String token = jwtUtil.generateAccessToken(userId, email, role, companyId);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> jwtUtil.extractUserId(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("extractUserId: should throw on completely invalid token")
    void extractUserId_shouldThrow_onGarbage() {
        assertThatThrownBy(() -> jwtUtil.extractUserId("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }
}
