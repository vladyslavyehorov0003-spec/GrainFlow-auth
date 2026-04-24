package com.grainflow.auth.service;

import com.grainflow.auth.TestFixtures;
import com.grainflow.auth.dto.request.LoginRequest;
import com.grainflow.auth.dto.request.RefreshTokenRequest;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.Company;
import com.grainflow.auth.entity.RefreshToken;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.CompanyRepository;
import com.grainflow.auth.repository.RefreshTokenRepository;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.impl.AuthServiceImpl;
import com.grainflow.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl")
class AuthServiceImplTest {

    @Mock private UserRepository          userRepository;
    @Mock private CompanyRepository       companyRepository;
    @Mock private RefreshTokenRepository  refreshTokenRepository;
    @Mock private JwtUtil                 jwtUtil;
    @Mock private PasswordEncoder         passwordEncoder;
    @Mock private AuthenticationManager   authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        // Inject @Value field that Mockito cannot inject automatically
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", 604_800_000L);
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: should save company + manager and return AuthResponse")
    void register_shouldSaveCompanyAndManager_andReturnAuthResponse() {
        var request = TestFixtures.registerRequest();
        var company = TestFixtures.company();
        var manager = TestFixtures.manager();

        when(companyRepository.existsByName(request.company().name())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenReturn(company);
        when(userRepository.existsByEmployeeId(anyString())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(manager);
        when(jwtUtil.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh-token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().email()).isEqualTo(manager.getEmail());
        verify(companyRepository).save(any(Company.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register: should throw 409 when company name is taken")
    void register_shouldThrowConflict_whenCompanyNameExists() {
        var request = TestFixtures.registerRequest();
        when(companyRepository.existsByName(request.company().name())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Company");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: should throw 409 when email is taken")
    void register_shouldThrowConflict_whenEmailExists() {
        var request = TestFixtures.registerRequest();
        when(companyRepository.existsByName(request.company().name())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("email");

        verify(companyRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: should return AuthResponse when credentials are valid")
    void login_shouldReturnAuthResponse_whenCredentialsValid() {
        var request = TestFixtures.loginRequest();
        var manager = TestFixtures.manager();

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(manager));
        when(jwtUtil.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login: should throw 401 when credentials are wrong")
    void login_shouldThrowUnauthorized_whenBadCredentials() {
        var request = TestFixtures.loginRequest();
        doThrow(BadCredentialsException.class)
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("login: should throw 403 when account is disabled")
    void login_shouldThrowForbidden_whenAccountDisabled() {
        var request = TestFixtures.loginRequest();
        var disabledUser = TestFixtures.manager();
        disabledUser.setEnabled(false);

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(403);
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: should rotate token and return new AuthResponse")
    void refresh_shouldRotateTokenAndReturnNewAuthResponse() {
        var manager      = TestFixtures.manager();
        var refreshToken = TestFixtures.validRefreshToken(manager);
        var request      = TestFixtures.refreshTokenRequest();

        when(refreshTokenRepository.findByToken(request.refreshToken()))
                .thenReturn(Optional.of(refreshToken));
        when(jwtUtil.generateAccessToken(any(), any(), any(), any())).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("new-refresh");

        AuthResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(refreshToken.isRevoked()).isTrue(); // old token revoked
    }

    @Test
    @DisplayName("refresh: should throw 401 when token is not found")
    void refresh_shouldThrowUnauthorized_whenTokenNotFound() {
        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("ghost-token")))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("refresh: should throw 401 when token is expired")
    void refresh_shouldThrowUnauthorized_whenTokenExpired() {
        var manager      = TestFixtures.manager();
        var expiredToken = TestFixtures.expiredRefreshToken(manager);

        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("expired-token")))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getStatus().value())
                .isEqualTo(401);
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: should revoke the refresh token")
    void logout_shouldRevokeRefreshToken() {
        var manager      = TestFixtures.manager();
        var refreshToken = TestFixtures.validRefreshToken(manager);

        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(refreshToken));

        authService.logout("valid-refresh-token");

        assertThat(refreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    @DisplayName("logout: should do nothing when token is not found")
    void logout_shouldDoNothing_whenTokenNotFound() {
        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> authService.logout("ghost-token"));
        verify(refreshTokenRepository, never()).save(any());
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("validate: should return valid=true with subscriptionStatus when user is present")
    void validate_shouldReturnValidResponse_whenUserPresent() {
        User manager = TestFixtures.manager();

        ValidateTokenResponse response = authService.validate(manager);

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo(manager.getId());
        assertThat(response.companyId()).isEqualTo(manager.getCompany().getId());
        assertThat(response.email()).isEqualTo(manager.getEmail());
        assertThat(response.role()).isEqualTo(Role.MANAGER);
        assertThat(response.subscriptionStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("validate: should return ACTIVE subscriptionStatus when company has active subscription")
    void validate_shouldReturnActiveSubscription_whenCompanyIsActive() {
        User manager = TestFixtures.manager();
        manager.getCompany().setSubscriptionStatus("ACTIVE");

        ValidateTokenResponse response = authService.validate(manager);

        assertThat(response.valid()).isTrue();
        assertThat(response.subscriptionStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("validate: should return valid=false with nulls when user is null")
    void validate_shouldReturnInvalidResponse_whenUserIsNull() {
        ValidateTokenResponse response = authService.validate(null);

        assertThat(response.valid()).isFalse();
        assertThat(response.userId()).isNull();
        assertThat(response.companyId()).isNull();
        assertThat(response.email()).isNull();
        assertThat(response.role()).isNull();
        assertThat(response.subscriptionStatus()).isNull();
    }
}
