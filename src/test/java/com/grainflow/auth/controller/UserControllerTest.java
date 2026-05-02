package com.grainflow.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.config.SecurityConfig;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.RequestEmailChangeResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.security.CustomUserDetailsService;
import com.grainflow.auth.service.UserService;
import com.grainflow.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired private MockMvc mockMvc;

    // JavaTimeModule required for LocalDateTime serialization in UserResponse
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @MockitoBean private UserService             userService;
    @MockitoBean private JwtUtil                 jwtUtil;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .defaultRequest(get("/").contextPath("/api/v1")) // Устанавливаем контекст по умолчанию
                .build();
    }
    // ── GET /me ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me: 200 with user profile when authenticated")
    void me_shouldReturn200_whenAuthenticated() throws Exception {
        var manager = TestFixtures.manager();

        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(manager.getEmail()))
                .andExpect(jsonPath("$.data.role").value("MANAGER"));
    }

    @Test
    @DisplayName("GET /me: 403 when not authenticated")
    void me_shouldReturn403_whenNotAuthenticated() throws Exception {

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /workers ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /workers: 201 when manager creates a worker")
    void createWorker_shouldReturn201_whenManager() throws Exception {
        var manager  = TestFixtures.manager();
        var worker   = TestFixtures.worker();
        var request  = TestFixtures.createWorkerRequest();

        when(userService.createWorker(any(), eq(TestFixtures.MANAGER_ID)))
                .thenReturn(UserResponse.from(worker));

        mockMvc.perform(post("/api/v1/users/workers")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(worker.getEmail()))
                .andExpect(jsonPath("$.data.role").value("WORKER"));
    }

    @Test
    @DisplayName("POST /workers: 403 when worker tries to create another worker")
    void createWorker_shouldReturn403_whenCalledByWorker() throws Exception {
        var worker  = TestFixtures.worker();
        var request = TestFixtures.createWorkerRequest();

        mockMvc.perform(post("/api/v1/users/workers")
                        .with(user(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /workers: 400 when request body is invalid")
    void createWorker_shouldReturn400_whenRequestInvalid() throws Exception {
        var manager = TestFixtures.manager();

        mockMvc.perform(post("/api/v1/users/workers")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Bob\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /workers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /workers: 403 when called by worker")
    void getWorkers_shouldReturn403_whenCalledByWorker() throws Exception {
        var worker = TestFixtures.worker();

        mockMvc.perform(get("/api/v1/users/workers")
                        .with(user(worker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /workers: 200 with list of workers when manager")
    void getWorkers_shouldReturn200_whenManager() throws Exception {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();

        when(userService.getWorkers(eq(TestFixtures.MANAGER_ID),any(),any()))
                .thenReturn(new PageImpl<>(List.of(UserResponse.from(worker))));

        mockMvc.perform(get("/api/v1/users/workers")
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value(worker.getEmail()));
    }

    // ── GET /workers/{id} ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /workers/{id}: 403 when called by worker")
    void getWorker_shouldReturn403_whenCalledByWorker() throws Exception {
        var worker = TestFixtures.worker();

        mockMvc.perform(get("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(worker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /workers/{id}: 200 when worker belongs to manager's company")
    void getWorker_shouldReturn200_whenManager() throws Exception {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();

        when(userService.getWorker(TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID))
                .thenReturn(UserResponse.from(worker));

        mockMvc.perform(get("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeId").value(worker.getEmployeeId()));
    }

    @Test
    @DisplayName("GET /workers/{id}: 403 when worker belongs to a different company")
    void getWorker_shouldReturn403_whenDifferentCompany() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.getWorker(any(), any()))
                .thenThrow(AuthException.forbidden("Worker does not belong to your company"));

        mockMvc.perform(get("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(manager)))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /workers/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /workers/{id}: 403 when called by worker")
    void updateWorker_shouldReturn403_whenCalledByWorker() throws Exception {
        var worker  = TestFixtures.worker();
        var request = TestFixtures.updateWorkerRequest();

        mockMvc.perform(patch("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /workers/{id}: 200 when update is valid")
    void updateWorker_shouldReturn200_whenValid() throws Exception {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();
        worker.setFirstName("Robert");
        var request = TestFixtures.updateWorkerRequest();

        when(userService.updateWorker(any(), eq(TestFixtures.WORKER_ID), eq(TestFixtures.MANAGER_ID)))
                .thenReturn(UserResponse.from(worker));

        mockMvc.perform(patch("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Robert"));
    }

    // ── DELETE /workers/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /workers/{id}: 403 when called by worker")
    void deleteWorker_shouldReturn403_whenCalledByWorker() throws Exception {
        var worker = TestFixtures.worker();

        mockMvc.perform(delete("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(worker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /workers/{id}: 200 and deactivation message")
    void deleteWorker_shouldReturn200() throws Exception {
        var manager = TestFixtures.manager();

        mockMvc.perform(delete("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Worker deactivated successfully"));
    }

    @Test
    @DisplayName("DELETE /workers/{id}: 404 when worker not found")
    void deleteWorker_shouldReturn404_whenWorkerNotFound() throws Exception {
        var manager = TestFixtures.manager();
        doThrow(AuthException.notFound("Worker not found"))
                .when(userService).deleteWorker(any(), any());

        mockMvc.perform(delete("/api/v1/users/workers/" + TestFixtures.WORKER_ID)
                        .with(user(manager)))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /me/password ────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /me/password: 200 with fresh tokens for any authenticated user (manager)")
    void changePassword_shouldReturn200_forManager() throws Exception {
        var manager = TestFixtures.manager();
        var request = TestFixtures.changePasswordRequest();

        when(userService.changePassword(eq(TestFixtures.MANAGER_ID), any()))
                .thenReturn(TestFixtures.authResponse(manager));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("PATCH /me/password: 200 also for worker (own account is accessible regardless of role)")
    void changePassword_shouldReturn200_forWorker() throws Exception {
        var worker  = TestFixtures.worker();
        var request = TestFixtures.changePasswordRequest();

        when(userService.changePassword(eq(TestFixtures.WORKER_ID), any()))
                .thenReturn(TestFixtures.authResponse(worker));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /me/password: 401 when current password is wrong")
    void changePassword_shouldReturn401_whenCurrentPasswordWrong() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.changePassword(any(), any()))
                .thenThrow(AuthException.unauthorized("Current password is incorrect"));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.changePasswordRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /me/password: 401 when not authenticated")
    void changePassword_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.changePasswordRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /me/password: 400 when newPassword is too short")
    void changePassword_shouldReturn400_whenInvalidBody() throws Exception {
        var manager = TestFixtures.manager();
        // newPassword shorter than the @Size(min = 8) requirement
        String body = "{\"currentPassword\":\"password123\",\"newPassword\":\"short\"}";

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── POST /me/email/request-change ─────────────────────────────────────────

    @Test
    @DisplayName("POST /me/email/request-change: 200 CODE_SENT for VERIFIED company")
    void requestEmailChange_shouldReturn200_codeSent() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.requestEmailChange(eq(TestFixtures.MANAGER_ID), any()))
                .thenReturn(RequestEmailChangeResponse.codeSent());

        mockMvc.perform(post("/api/v1/users/me/email/request-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.requestEmailChangeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CODE_SENT"))
                .andExpect(jsonPath("$.data.tokens").doesNotExist());
    }

    @Test
    @DisplayName("POST /me/email/request-change: 200 CHANGED with tokens for UNVERIFIED company")
    void requestEmailChange_shouldReturn200_changedWithTokens() throws Exception {
        var manager = TestFixtures.manager();
        AuthResponse tokens = TestFixtures.authResponse(manager);
        when(userService.requestEmailChange(eq(TestFixtures.MANAGER_ID), any()))
                .thenReturn(RequestEmailChangeResponse.changed(tokens));

        mockMvc.perform(post("/api/v1/users/me/email/request-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.requestEmailChangeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHANGED"))
                .andExpect(jsonPath("$.data.tokens.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /me/email/request-change: 400 when newEmail is malformed")
    void requestEmailChange_shouldReturn400_whenInvalidEmail() throws Exception {
        var manager = TestFixtures.manager();

        mockMvc.perform(post("/api/v1/users/me/email/request-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /me/email/request-change: 401 when not authenticated")
    void requestEmailChange_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/email/request-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.requestEmailChangeRequest())))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /me/email/confirm-change ─────────────────────────────────────────

    @Test
    @DisplayName("POST /me/email/confirm-change: 200 with fresh tokens")
    void confirmEmailChange_shouldReturn200_withTokens() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.confirmEmailChange(eq(TestFixtures.MANAGER_ID), any()))
                .thenReturn(TestFixtures.authResponse(manager));

        mockMvc.perform(post("/api/v1/users/me/email/confirm-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.confirmEmailChangeRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /me/email/confirm-change: 401 when code is wrong")
    void confirmEmailChange_shouldReturn401_whenCodeWrong() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.confirmEmailChange(any(), any()))
                .thenThrow(AuthException.unauthorized("Invalid code"));

        mockMvc.perform(post("/api/v1/users/me/email/confirm-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.confirmEmailChangeRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /me/email/confirm-change: 403 when token belongs to another user (cross-user)")
    void confirmEmailChange_shouldReturn403_whenForeignToken() throws Exception {
        var manager = TestFixtures.manager();
        when(userService.confirmEmailChange(any(), any()))
                .thenThrow(AuthException.forbidden("This confirmation link does not belong to your account"));

        mockMvc.perform(post("/api/v1/users/me/email/confirm-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestFixtures.confirmEmailChangeRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /me/email/confirm-change: 400 when code is not 6 digits")
    void confirmEmailChange_shouldReturn400_whenInvalidCode() throws Exception {
        var manager = TestFixtures.manager();
        String body = "{\"token\":\"abc\",\"code\":\"123\"}"; // code too short

        mockMvc.perform(post("/api/v1/users/me/email/confirm-change")
                        .with(user(manager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
