package com.grainflow.auth.service;

import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.request.UserFilterRequest;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.impl.UserServiceImpl;
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.grainflow.auth.entity.Role.WORKER;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock private UserRepository  userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    // ── createWorker ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("createWorker: should save worker and return UserResponse")
    void createWorker_shouldSaveWorker_andReturnResponse() {
        var request = TestFixtures.createWorkerRequest();
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.existsByEmployeeId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(worker);

        UserResponse response = userService.createWorker(request, TestFixtures.MANAGER_ID);

        assertThat(response.email()).isEqualTo(worker.getEmail());
        assertThat(response.role()).isEqualTo(WORKER);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createWorker: should throw 409 when email already exists")
    void createWorker_shouldThrowConflict_whenEmailExists() {
        var request = TestFixtures.createWorkerRequest();
        var manager = TestFixtures.manager();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.createWorker(request, TestFixtures.MANAGER_ID))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(409);

        verify(userRepository, never()).save(any());
    }

    // ── getWorkers ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getWorkers: should return all workers in the manager's company")
    void getWorkers_shouldReturnWorkerList() {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();
        var pageRequest = PageRequest.of(0, 20);
        var workerPage = new PageImpl<>(List.of(worker), pageRequest, 1);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));

//        when(userRepository.findAll(TestFixtures.COMPANY_ID, WORKER))
//                .thenReturn(new PageImpl<>(List.of(worker)));

        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(workerPage);
        Page<UserResponse> result = userService.getWorkers(TestFixtures.MANAGER_ID,emptyFilter(), PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo(worker.getEmail());
    }

    // ── getWorker ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getWorker: should return worker when it belongs to manager's company")
    void getWorker_shouldReturnWorker_whenBelongsToCompany() {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(worker));

        UserResponse result = userService.getWorker(TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID);

        assertThat(result.id()).isEqualTo(worker.getId());
    }

    @Test
    @DisplayName("getWorker: should throw 403 when worker belongs to a different company")
    void getWorker_shouldThrowForbidden_whenDifferentCompany() {
        var manager = TestFixtures.manager();

        // Worker with a different company
        var otherCompany = TestFixtures.company();
        otherCompany.setId(UUID.randomUUID());
        var alienWorker = TestFixtures.worker();
        alienWorker.setCompany(otherCompany);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(alienWorker));

        assertThatThrownBy(() -> userService.getWorker(TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(403);
    }

    // ── updateWorker ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateWorker: should apply only non-null fields")
    void updateWorker_shouldApplyOnlyProvidedFields() {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();
        // Update only firstName, leave everything else null
        var request = new UpdateWorkerRequest("Robert", null, null, null, null,null);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(worker));
        when(userRepository.save(worker)).thenReturn(worker);

        UserResponse result = userService.updateWorker(request, TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID);

        assertThat(worker.getFirstName()).isEqualTo("Robert");
        assertThat(worker.getLastName()).isEqualTo("Jones"); // unchanged
        assertThat(result.firstName()).isEqualTo("Robert");
    }

    @Test
    @DisplayName("updateWorker: should throw 409 when new email is already taken by another user")
    void updateWorker_shouldThrowConflict_whenEmailAlreadyTaken() {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();
        var request = new UpdateWorkerRequest(null, null, "taken@example.com", null, null,null);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(worker));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateWorker(request, TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(409);
    }

    // ── deleteWorker ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteWorker: should set enabled=false (soft delete)")
    void deleteWorker_shouldDisableAccount() {
        var manager = TestFixtures.manager();
        var worker  = TestFixtures.worker();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(worker));

        userService.deleteWorker(TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID);

        assertThat(worker.isEnabled()).isFalse();
        verify(userRepository).save(worker);
    }

    @Test
    @DisplayName("deleteWorker: should throw 403 when worker belongs to a different company")
    void deleteWorker_shouldThrowForbidden_whenDifferentCompany() {
        var manager = TestFixtures.manager();
        var otherCompany = TestFixtures.company();
        otherCompany.setId(UUID.randomUUID());
        var alienWorker = TestFixtures.worker();
        alienWorker.setCompany(otherCompany);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(alienWorker));

        assertThatThrownBy(() -> userService.deleteWorker(TestFixtures.WORKER_ID, TestFixtures.MANAGER_ID))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(403);

        verify(userRepository, never()).save(any());
    }
    private UserFilterRequest emptyFilter() {
        return new UserFilterRequest(null, null);
    }
}
