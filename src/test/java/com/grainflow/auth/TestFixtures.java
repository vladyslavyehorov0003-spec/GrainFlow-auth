package com.grainflow.auth;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.entity.Company;
import com.grainflow.auth.entity.RefreshToken;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

// Shared test data factory — keeps tests DRY and consistent
public final class TestFixtures {

    private TestFixtures() {}

    public static final UUID COMPANY_ID  = UUID.randomUUID();
    public static final UUID MANAGER_ID  = UUID.randomUUID();
    public static final UUID WORKER_ID   = UUID.randomUUID();

    // ── Entities ──────────────────────────────────────────────────────────────

    public static Company company() {
        Company c = new Company();
        c.setId(COMPANY_ID);
        c.setName("GrainFlow LLC");
        c.setAddress("123 Wheat St");
        c.setPhone("+1-555-0100");
        return c;
    }

    public static User manager() {
        return User.builder()
                .id(MANAGER_ID)
                .company(company())
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@grainflow.com")
                .password("$2a$10$hashed_password")
                .role(Role.MANAGER)
                .employeeId("EMP-AAAAAAAA")
                .enabled(true)
                .build();
    }

    public static User worker() {
        return User.builder()
                .id(WORKER_ID)
                .company(company())
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@grainflow.com")
                .password("$2a$10$hashed_password")
                .pin("$2a$10$hashed_pin")
                .role(Role.WORKER)
                .employeeId("EMP-BBBBBBBB")
                .enabled(true)
                .build();
    }

    public static RefreshToken validRefreshToken(User user) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("valid-refresh-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
    }

    public static RefreshToken expiredRefreshToken(User user) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("expired-refresh-token")
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();
    }

    // ── Requests ──────────────────────────────────────────────────────────────

    public static RegisterRequest registerRequest() {
        return new RegisterRequest(
                "Alice",
                "Smith",
                "alice@grainflow.com",
                "password123",
                new CompanyRequest("GrainFlow LLC", "123 Wheat St", "+1-555-0100")
        );
    }

    public static LoginRequest loginRequest() {
        return new LoginRequest("alice@grainflow.com", "password123");
    }

    public static UserFilterRequest  userFilterRequest() {
        return new  UserFilterRequest(null,null);
    }

    public static CreateWorkerRequest createWorkerRequest() {
        return new CreateWorkerRequest(
                "Bob",
                "Jones",
                "bob@grainflow.com",
                "password123",
                "1234"
        );
    }

    public static UpdateWorkerRequest updateWorkerRequest() {
        return new UpdateWorkerRequest("Robert", null, null, null, null,null);
    }

    public static RefreshTokenRequest refreshTokenRequest() {
        return new RefreshTokenRequest("valid-refresh-token");
    }

    // ── Responses ─────────────────────────────────────────────────────────────

    public static UserResponse userResponse(User user) {
        return UserResponse.from(user);
    }

    public static AuthResponse authResponse(User user) {
        return new AuthResponse(
                "access-token",
                "refresh-token",
                900L,
                UserResponse.from(user)
        );
    }
}
