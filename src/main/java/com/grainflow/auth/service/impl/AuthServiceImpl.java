package com.grainflow.auth.service.impl;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.Company;
import com.grainflow.auth.entity.RefreshToken;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.CompanyRepository;
import com.grainflow.auth.repository.RefreshTokenRepository;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.AuthService;
import com.grainflow.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;


    // Explicit transaction — if company is saved but user creation fails,
    // the entire operation rolls back to prevent orphaned company records
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse register(RegisterRequest request) {
        // Ensure no duplicate company names exist
        if (companyRepository.existsByName(request.company().name())) {
            throw AuthException.conflict("Company with this name already exists");
        }

        // Ensure no duplicate emails across all users
        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.conflict("User with this email already exists");
        }

        // Create and persist the company
        Company company = companyRepository.save(Company.builder()
                .name(request.company().name())
                .address(request.company().address())
                .phone(request.company().phone())
                .build());

        // Create the manager — first user of the company
        // If this fails, the company save above will also be rolled back
        User manager = userRepository.save(User.builder()
                .company(company)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.MANAGER)
                .employeeId(generateEmployeeId())
                .enabled(true)
                .build());

        log.info("Manager registered: {} for company: {}", manager.getEmail(), company.getName());

        return buildAuthResponse(manager);
    }


    @Override
    public AuthResponse login(LoginRequest request) {
        // Delegate credential verification to Spring Security
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            throw AuthException.unauthorized("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> AuthException.notFound("User not found"));

        if (!user.isEnabled()) {
            throw AuthException.forbidden("Account is disabled");
        }

        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user);
    }



    @Override
    public AuthResponse terminalLogin(WorkerLoginRequest request) {
        // TODO: find user by employeeId
        // TODO: verify PIN using BCrypt
        // TODO: generate access + refresh tokens (short-lived for terminal sessions)
        throw AuthException.notImplemented("Terminal login is not implemented yet");
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse refresh(RefreshTokenRequest request) {
        // Find the refresh token record in the database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> AuthException.unauthorized("Refresh token not found"));

        // Reject if token was revoked or expired
        if (!refreshToken.isValid()) {
            throw AuthException.unauthorized("Refresh token is expired or revoked");
        }

        User user = refreshToken.getUser();

        if (!user.isEnabled()) {
            throw AuthException.forbidden("Account is disabled");
        }

        // Rotate refresh token — revoke old one and issue a new one
        // This limits the window of misuse if a refresh token is stolen
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String newRefreshTokenValue = jwtUtil.generateRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .token(newRefreshTokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build());

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole(), user.getCompany().getId()
        );

        log.info("Token refreshed for user: {}", user.getEmail());

        return new AuthResponse(
                newAccessToken,
                newRefreshTokenValue,
                refreshTokenExpiration / 1000,
                UserResponse.from(user)
        );
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Override
    public void logout(String refreshToken) {
        // Find the token and mark it as revoked — subsequent refresh attempts will be rejected
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("User logged out: {}", token.getUser().getEmail());
        });
    }

    // ── Validate ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ValidateTokenResponse validate(User currentUser) {
        // currentUser is null when JwtAuthFilter couldn't authenticate the request
        if (currentUser == null) {
            return new ValidateTokenResponse(false, null, null, null, null);
        }
        return new ValidateTokenResponse(
                true,
                currentUser.getId(),
                currentUser.getCompany().getId(),
                currentUser.getEmail(),
                currentUser.getRole()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Builds AuthResponse by generating tokens and persisting the refresh token
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getCompany().getId()
        );

        String refreshTokenValue = jwtUtil.generateRefreshToken(user.getId());

        // Revoke all previous refresh tokens for this user before saving new one
        refreshTokenRepository.revokeAllByUser(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000))
                .revoked(false)
                .build());

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                refreshTokenExpiration / 1000,
                UserResponse.from(user)
        );
    }

    // Generates a unique employee ID in format EMP-XXXXXXXX
    private String generateEmployeeId() {
        String employeeId;
        do {
            employeeId = "EMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (userRepository.existsByEmployeeId(employeeId));
        return employeeId;
    }
}
