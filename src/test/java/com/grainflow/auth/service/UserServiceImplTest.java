package com.grainflow.auth.service;

import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.dto.request.ChangePasswordRequest;
import com.grainflow.auth.dto.request.ConfirmEmailChangeRequest;
import com.grainflow.auth.dto.request.RequestEmailChangeRequest;
import com.grainflow.auth.dto.request.UpdateWorkerRequest;
import com.grainflow.auth.dto.request.UserFilterRequest;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.RequestEmailChangeResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.entity.Company;
import com.grainflow.auth.entity.EmailChangeCode;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.CompanyRepository;
import com.grainflow.auth.repository.EmailChangeCodeRepository;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock private UserRepository              userRepository;
    @Mock private CompanyRepository           companyRepository;
    @Mock private EmailChangeCodeRepository   emailChangeCodeRepository;
    @Mock private AuthService                 authService;
    @Mock private EmailService                emailService;
    @Mock private PasswordEncoder             passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        // @Value-injected fields — Mockito doesn't fill these via @InjectMocks
        ReflectionTestUtils.setField(userService, "emailChangeCodeExpiryMinutes", 60);
        ReflectionTestUtils.setField(userService, "verificationTokenExpiryHours", 24);
    }

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

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword: should update password and return fresh AuthResponse")
    void changePassword_shouldUpdatePasswordAndReturnFreshTokens() {
        var manager = TestFixtures.manager();
        var request = TestFixtures.changePasswordRequest();
        var freshTokens = TestFixtures.authResponse(manager);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(passwordEncoder.matches(request.currentPassword(), manager.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(request.newPassword(), manager.getPassword())).thenReturn(false);
        when(passwordEncoder.encode(request.newPassword())).thenReturn("hashed-new");
        when(authService.issueTokensFor(manager)).thenReturn(freshTokens);

        AuthResponse result = userService.changePassword(TestFixtures.MANAGER_ID, request);

        assertThat(manager.getPassword()).isEqualTo("hashed-new");
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(userRepository).save(manager);
        verify(authService).issueTokensFor(manager);
    }

    @Test
    @DisplayName("changePassword: should throw 401 when current password is wrong")
    void changePassword_shouldThrowUnauthorized_whenCurrentPasswordWrong() {
        var manager = TestFixtures.manager();
        var request = TestFixtures.changePasswordRequest();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(passwordEncoder.matches(request.currentPassword(), manager.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(401);

        verify(userRepository, never()).save(any());
        verify(authService, never()).issueTokensFor(any());
    }

    @Test
    @DisplayName("changePassword: should throw 400 when new password equals current password")
    void changePassword_shouldThrowBadRequest_whenNewPasswordSameAsCurrent() {
        var manager = TestFixtures.manager();
        var request = TestFixtures.changePasswordRequest();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(passwordEncoder.matches(request.currentPassword(), manager.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(request.newPassword(), manager.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(400);
    }

    // ── requestEmailChange — UNVERIFIED path ──────────────────────────────────

    @Test
    @DisplayName("requestEmailChange: UNVERIFIED manager — change immediately + send verification link to NEW email")
    void requestEmailChange_unverifiedManager_shouldChangeImmediatelyAndSendVerification() {
        var manager = TestFixtures.manager(); // company verificationStatus = UNVERIFIED
        var request = TestFixtures.requestEmailChangeRequest();
        var freshTokens = TestFixtures.authResponse(manager);

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(false);
        when(authService.issueTokensFor(manager)).thenReturn(freshTokens);

        RequestEmailChangeResponse result = userService.requestEmailChange(TestFixtures.MANAGER_ID, request);

        assertThat(result.status()).isEqualTo("CHANGED");
        assertThat(result.tokens()).isNotNull();
        assertThat(manager.getEmail()).isEqualTo(request.newEmail());
        // No challenge persisted (immediate path bypasses email_change_codes)
        verify(emailChangeCodeRepository, never()).save(any());
        // Verification link goes to the NEW email so the manager can re-verify their company
        verify(emailService).sendVerificationEmail(eq(request.newEmail()), anyString());
        verify(companyRepository).save(manager.getCompany());
    }

    @Test
    @DisplayName("requestEmailChange: UNVERIFIED worker — change immediately, NO verification email")
    void requestEmailChange_unverifiedWorker_shouldChangeWithoutVerificationEmail() {
        var worker = TestFixtures.worker(); // company UNVERIFIED, role WORKER
        var request = new RequestEmailChangeRequest("bob.new@grainflow.com");
        var freshTokens = TestFixtures.authResponse(worker);

        when(userRepository.findById(TestFixtures.WORKER_ID)).thenReturn(Optional.of(worker));
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(false);
        when(authService.issueTokensFor(worker)).thenReturn(freshTokens);

        RequestEmailChangeResponse result = userService.requestEmailChange(TestFixtures.WORKER_ID, request);

        assertThat(result.status()).isEqualTo("CHANGED");
        assertThat(worker.getEmail()).isEqualTo(request.newEmail());
        // Workers don't trigger company verification — only managers do
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
        verify(companyRepository, never()).save(any());
    }

    // ── requestEmailChange — VERIFIED path (2-factor) ─────────────────────────

    @Test
    @DisplayName("requestEmailChange: VERIFIED — issues link to OLD + code to NEW (2-factor)")
    void requestEmailChange_verified_shouldIssueLinkToOldAndCodeToNew() {
        var manager = TestFixtures.manager();
        manager.setCompany(TestFixtures.verifiedCompany());
        String oldEmail = manager.getEmail();
        var request = TestFixtures.requestEmailChangeRequest();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        RequestEmailChangeResponse result = userService.requestEmailChange(TestFixtures.MANAGER_ID, request);

        assertThat(result.status()).isEqualTo("CODE_SENT");
        assertThat(result.tokens()).isNull();
        // Email is NOT applied yet — only after confirm
        assertThat(manager.getEmail()).isEqualTo(oldEmail);

        verify(emailChangeCodeRepository).deleteByUserId(TestFixtures.MANAGER_ID);
        verify(emailChangeCodeRepository).save(any(EmailChangeCode.class));
        verify(emailService).sendEmailChangeLink(eq(oldEmail), anyString(), eq(request.newEmail()));
        verify(emailService).sendEmailChangeCode(eq(request.newEmail()), anyString());
        verify(authService, never()).issueTokensFor(any());
    }

    @Test
    @DisplayName("requestEmailChange: 400 when new email equals current email")
    void requestEmailChange_shouldThrowBadRequest_whenSameEmail() {
        var manager = TestFixtures.manager();
        var sameEmail = new RequestEmailChangeRequest(manager.getEmail());

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> userService.requestEmailChange(TestFixtures.MANAGER_ID, sameEmail))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("requestEmailChange: 409 when new email is already taken")
    void requestEmailChange_shouldThrowConflict_whenEmailTaken() {
        var manager = TestFixtures.manager();
        var request = TestFixtures.requestEmailChangeRequest();

        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(request.newEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.requestEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(409);

        verify(emailChangeCodeRepository, never()).save(any());
        verify(emailService, never()).sendEmailChangeLink(any(), any(), any());
    }

    // ── confirmEmailChange ────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmEmailChange: success — applies new email and returns fresh tokens")
    void confirmEmailChange_shouldApplyNewEmailAndReturnTokens() {
        var manager = TestFixtures.manager();
        manager.setCompany(TestFixtures.verifiedCompany());
        var request = TestFixtures.confirmEmailChangeRequest();
        var pending = TestFixtures.emailChangeCode(
                TestFixtures.MANAGER_ID, request.token(), "hashed-code", "alice.new@grainflow.com");
        var freshTokens = TestFixtures.authResponse(manager);

        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(request.code(), pending.getCodeHash())).thenReturn(true);
        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(pending.getNewEmail())).thenReturn(false);
        when(authService.issueTokensFor(manager)).thenReturn(freshTokens);

        AuthResponse result = userService.confirmEmailChange(TestFixtures.MANAGER_ID, request);

        assertThat(manager.getEmail()).isEqualTo(pending.getNewEmail());
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(emailChangeCodeRepository).delete(pending);
    }

    @Test
    @DisplayName("confirmEmailChange: 400 when no pending request for token")
    void confirmEmailChange_shouldThrowBadRequest_whenTokenNotFound() {
        var request = TestFixtures.confirmEmailChangeRequest();
        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.confirmEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("confirmEmailChange: 403 when token belongs to a different user (cross-user attack)")
    void confirmEmailChange_shouldThrowForbidden_whenTokenBelongsToOtherUser() {
        var request = TestFixtures.confirmEmailChangeRequest();
        // Token is for SOMEONE ELSE (workerId), but the authenticated user is the manager
        var pending = TestFixtures.emailChangeCode(
                TestFixtures.WORKER_ID, request.token(), "hashed-code", "alice.new@grainflow.com");

        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> userService.confirmEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(403);

        // Code should NOT be consumed — leave it for the rightful owner
        verify(emailChangeCodeRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmEmailChange: 400 when challenge has expired")
    void confirmEmailChange_shouldThrowBadRequest_whenExpired() {
        var request = TestFixtures.confirmEmailChangeRequest();
        var pending = TestFixtures.emailChangeCode(
                TestFixtures.MANAGER_ID, request.token(), "hashed-code", "alice.new@grainflow.com");
        pending.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));

        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> userService.confirmEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(400);

        // Expired challenge should be cleaned up
        verify(emailChangeCodeRepository).delete(pending);
    }

    @Test
    @DisplayName("confirmEmailChange: 401 when code does not match")
    void confirmEmailChange_shouldThrowUnauthorized_whenCodeWrong() {
        var request = TestFixtures.confirmEmailChangeRequest();
        var pending = TestFixtures.emailChangeCode(
                TestFixtures.MANAGER_ID, request.token(), "hashed-code", "alice.new@grainflow.com");

        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(request.code(), pending.getCodeHash())).thenReturn(false);

        assertThatThrownBy(() -> userService.confirmEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(401);

        // Don't burn the challenge on a wrong attempt — user can retry
        verify(emailChangeCodeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("confirmEmailChange: 409 if the new email got grabbed by another user since request")
    void confirmEmailChange_shouldThrowConflict_whenNewEmailNoLongerAvailable() {
        var manager = TestFixtures.manager();
        var request = TestFixtures.confirmEmailChangeRequest();
        var pending = TestFixtures.emailChangeCode(
                TestFixtures.MANAGER_ID, request.token(), "hashed-code", "alice.new@grainflow.com");

        when(emailChangeCodeRepository.findByToken(request.token())).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches(request.code(), pending.getCodeHash())).thenReturn(true);
        when(userRepository.findById(TestFixtures.MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.existsByEmail(pending.getNewEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.confirmEmailChange(TestFixtures.MANAGER_ID, request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(409);

        verify(emailChangeCodeRepository).delete(pending);
        verify(authService, never()).issueTokensFor(any());
    }
}
