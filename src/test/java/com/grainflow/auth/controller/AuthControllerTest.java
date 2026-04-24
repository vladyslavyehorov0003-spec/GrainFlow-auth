package com.grainflow.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.config.SecurityConfig;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.security.CustomUserDetailsService;
import com.grainflow.auth.service.AuthService;
import com.grainflow.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext wac;

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .defaultRequest(get("/").contextPath("/api/v1")) // Устанавливаем контекст по умолчанию
                .build();
    }
    @Autowired private MockMvc mockMvc;

    // JavaTimeModule required for LocalDateTime serialization in UserResponse
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @MockitoBean private AuthService             authService;
    @MockitoBean private JwtUtil                 jwtUtil;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    // ── POST /register ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /register: 201 when request is valid")
    void register_shouldReturn201_whenValid() throws Exception {
        var request  = TestFixtures.registerRequest();
        var manager  = TestFixtures.manager();
        var response = TestFixtures.authResponse(manager);

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /register: 400 when email is missing")
    void register_shouldReturn400_whenEmailMissing() throws Exception {
        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "password": "password123",
                  "company": { "name": "Co", "address": "Addr", "phone": "123" }
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("POST /register: 400 when password is too short")
    void register_shouldReturn400_whenPasswordTooShort() throws Exception {
        String body = """
                {
                  "firstName": "Alice",
                  "lastName": "Smith",
                  "email": "alice@test.com",
                  "password": "short",
                  "company": { "name": "Co", "address": "Addr", "phone": "123" }
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register: 409 when company name is already taken")
    void register_shouldReturn409_whenCompanyExists() throws Exception {
        var request = TestFixtures.registerRequest();
        when(authService.register(any()))
                .thenThrow(AuthException.conflict("Company with this name already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Company with this name already exists"));
    }

    // ── POST /login ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 200 when credentials are valid")
    void login_shouldReturn200_whenValid() throws Exception {
        var request  = TestFixtures.loginRequest();
        var manager  = TestFixtures.manager();
        var response = TestFixtures.authResponse(manager);

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /login: 401 when credentials are invalid")
    void login_shouldReturn401_whenBadCredentials() throws Exception {
        var request = TestFixtures.loginRequest();
        when(authService.login(any()))
                .thenThrow(AuthException.unauthorized("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /refresh ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /refresh: 200 when refresh token is valid")
    void refresh_shouldReturn200_whenValid() throws Exception {
        var request  = TestFixtures.refreshTokenRequest();
        var manager  = TestFixtures.manager();
        var response = TestFixtures.authResponse(manager);

        when(authService.refresh(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /refresh: 401 when refresh token is expired")
    void refresh_shouldReturn401_whenTokenExpired() throws Exception {
        var request = TestFixtures.refreshTokenRequest();
        when(authService.refresh(any()))
                .thenThrow(AuthException.unauthorized("Refresh token is expired or revoked"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /logout ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout: 200 and success message")
    void logout_shouldReturn200() throws Exception {
        var manager = TestFixtures.manager();
        var request = TestFixtures.refreshTokenRequest();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    // ── GET /validate ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /validate: 200 with valid=true when token is valid")
    void validate_shouldReturn200_withValidTrue_whenAuthenticated() throws Exception {
        var manager = TestFixtures.manager();
        var response = new ValidateTokenResponse(
                true,
                manager.getId(),
                manager.getCompany().getId(),
                manager.getEmail(),
                Role.MANAGER,"ACTIVE"
        );

        when(authService.validate(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/validate")
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.email").value(manager.getEmail()))
                .andExpect(jsonPath("$.data.role").value("MANAGER"))
                .andExpect(jsonPath("$.message").value("Token is valid"));
    }

    @Test
    @DisplayName("GET /validate: 200 with valid=false when no token provided")
    void validate_shouldReturn200_withValidFalse_whenNoToken() throws Exception {
        var response = new ValidateTokenResponse(false, null, null, null, null,null);
        when(authService.validate(null)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.message").value("Token is missing or invalid"));
    }
}
