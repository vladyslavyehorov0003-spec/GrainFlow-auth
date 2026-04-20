package com.grainflow.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.config.SecurityConfig;
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
}
