package com.grainflow.auth.service.impl;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.UserResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.Company;
import com.grainflow.auth.entity.PasswordResetToken;
import com.grainflow.auth.entity.RefreshToken;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.exception.AuthException;
import com.grainflow.auth.repository.CompanyRepository;
import com.grainflow.auth.repository.PasswordResetTokenRepository;
import com.grainflow.auth.repository.RefreshTokenRepository;
import com.grainflow.auth.repository.UserRepository;
import com.grainflow.auth.service.AuthService;
import com.grainflow.auth.service.EmailService;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${app.verification-token-expiry-hours}")
    private int verificationTokenExpiryHours;

    @Value("${app.password-reset-token-expiry-minutes}")
    private int passwordResetTokenExpiryMinutes;


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

        String verificationToken = UUID.randomUUID().toString();
        company.setVerificationToken(verificationToken);
        company.setVerificationTokenExpiry(LocalDateTime.now().plusHours(verificationTokenExpiryHours));
        company.setVerificationStatus("UNVERIFIED");
        companyRepository.save(company);

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

        emailService.sendVerificationEmail(manager.getEmail(), verificationToken);

        return issueTokensFor(manager);
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

        return issueTokensFor(user);
    }



    @Override
    @Transactional
    public AuthResponse verifyEmail(String token) {
        Company company = companyRepository.findByVerificationToken(token)
                .orElseThrow(() -> AuthException.badRequest("Invalid or expired verification link"));

        if ("VERIFIED".equals(company.getVerificationStatus())) {
            throw AuthException.conflict("Company is already verified");
        }

        if (company.getVerificationTokenExpiry() == null ||
                LocalDateTime.now().isAfter(company.getVerificationTokenExpiry())) {
            throw AuthException.badRequest("Verification link has expired. Please request a new one.");
        }

        company.setVerificationStatus("VERIFIED");
        company.setVerificationToken(null);
        company.setVerificationTokenExpiry(null);
        companyRepository.save(company);

        log.info("Company verified: {}", company.getName());

        // Return tokens for the manager so they're logged in immediately after verification
        User manager = userRepository.findAllByCompanyIdAndRole(company.getId(), Role.MANAGER)
                .stream().findFirst()
                .orElseThrow(() -> AuthException.notFound("Manager not found for company"));

        return issueTokensFor(manager);
    }

    @Override
    @Transactional
    public void resendVerification(String email) {
        User manager = userRepository.findByEmail(email)
                .orElseThrow(() -> AuthException.notFound("User not found"));

        Company company = manager.getCompany();

        if ("VERIFIED".equals(company.getVerificationStatus())) {
            throw AuthException.conflict("Company is already verified");
        }

        String newToken = UUID.randomUUID().toString();
        company.setVerificationToken(newToken);
        company.setVerificationTokenExpiry(LocalDateTime.now().plusHours(verificationTokenExpiryHours));
        companyRepository.save(company);

        emailService.sendVerificationEmail(manager.getEmail(), newToken);
        log.info("Verification email resent to manager: {} for company: {}", manager.getEmail(), company.getName());
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
            return new ValidateTokenResponse(false, null, null, null, null, null, false);
        }
        return new ValidateTokenResponse(
                true,
                currentUser.getId(),
                currentUser.getCompany().getId(),
                currentUser.getEmail(),
                currentUser.getRole(),
                currentUser.getCompany().getSubscriptionStatus(),
                "VERIFIED".equals(currentUser.getCompany().getVerificationStatus())
        );
    }

    // ── Forgot / Reset password ───────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forgotPassword(ForgotPasswordRequest request) {
        // ALWAYS return silently — never reveal which emails are registered.
        // The HTTP layer always answers 200 regardless of what we do here.
        userRepository.findByEmail(request.email().trim().toLowerCase()).ifPresent(user -> {

            // Only verified accounts can reset — an unverified user just registered
            // and presumably still remembers their password; if not, they can re-verify.
            if (!"VERIFIED".equals(user.getCompany().getVerificationStatus())) {
                log.info("Password reset requested for UNVERIFIED account, ignoring: {}", user.getEmail());
                return;
            }

            String rawToken = UUID.randomUUID().toString();
            String tokenHash = sha256(rawToken);

            // Wipe any prior token for this user — only one valid link at a time.
            passwordResetTokenRepository.deleteByUserId(user.getId());
            passwordResetTokenRepository.save(PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenExpiryMinutes))
                    .used(false)
                    .build());

            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
            log.info("Password reset link issued for user: {}", user.getEmail());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.token());

        PasswordResetToken record = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> AuthException.badRequest("Invalid or expired reset link"));

        if (record.isUsed()) {
            throw AuthException.badRequest("This reset link has already been used");
        }
        if (LocalDateTime.now().isAfter(record.getExpiresAt())) {
            throw AuthException.badRequest("Reset link has expired. Please request a new one.");
        }

        User user = userRepository.findById(record.getUserId())
                .orElseThrow(() -> AuthException.notFound("User not found"));

        // Don't allow resetting to the same password — pointless and prevents
        // accidental no-ops where the user thinks something happened but nothing did.
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw AuthException.badRequest("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Mark token used so the same link can't be replayed.
        record.setUsed(true);
        passwordResetTokenRepository.save(record);

        // Kill every active session — if the password was reset, any old session
        // could be an attacker who briefly had access.
        refreshTokenRepository.revokeAllByUser(user);

        log.info("Password reset completed for user: {}", user.getEmail());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Deterministic hash so we can look the token up by its hash. BCrypt won't
    // work here (each call produces a different hash for the same input).
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Builds AuthResponse by generating tokens and persisting the refresh token.
    // Public + @Override because UserService also uses this on password change
    // (current device gets fresh tokens, all other devices logged out).
    @Override
    public AuthResponse issueTokensFor(User user) {
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
