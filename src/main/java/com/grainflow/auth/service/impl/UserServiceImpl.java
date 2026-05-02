package com.grainflow.auth.service.impl;

import com.grainflow.auth.dto.request.ChangePasswordRequest;
import com.grainflow.auth.dto.request.ConfirmEmailChangeRequest;
import com.grainflow.auth.dto.request.CreateWorkerRequest;
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
import com.grainflow.auth.repository.UserSpecification;
import com.grainflow.auth.service.AuthService;
import com.grainflow.auth.service.EmailService;
import com.grainflow.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {


    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmailChangeCodeRepository emailChangeCodeRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.email-change-code-expiry-minutes}")
    private int emailChangeCodeExpiryMinutes;

    @Value("${app.verification-token-expiry-hours}")
    private int verificationTokenExpiryHours;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse createWorker(CreateWorkerRequest request, UUID managerId) {
        // Load manager to resolve which company the worker belongs to
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        if (userRepository.existsByEmail(request.email())) {
            throw AuthException.conflict("User with this email already exists");
        }

        User worker = userRepository.save(User.builder()
                .company(manager.getCompany())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                // PIN is hashed — never stored in plain text
                .pin(passwordEncoder.encode(request.pin()))
                .role(Role.WORKER)
                .employeeId(generateEmployeeId())
                .enabled(true)
                .build());

        log.info("Worker created: {} in company: {}", worker.getEmail(), manager.getCompany().getName());

        return UserResponse.from(worker);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getWorkers(UUID managerId, UserFilterRequest filter, Pageable pageable) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        return userRepository
                .findAll(UserSpecification.filter(manager.getCompany().getId(), filter), pageable)
                .map(UserResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getWorker(UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        // Ensure the worker belongs to the manager's company
        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        return UserResponse.from(worker);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateWorker(UpdateWorkerRequest request, UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        // Apply only the fields that were provided — null means "keep existing value"
        if (request.firstName() != null) worker.setFirstName(request.firstName());
        if (request.lastName()  != null) worker.setLastName(request.lastName());
        if (request.email()     != null) {
            if (userRepository.existsByEmail(request.email()) && !worker.getEmail().equals(request.email())) {
                throw AuthException.conflict("Email is already taken");
            }
            worker.setEmail(request.email());
        }
        if (request.enabled() != null) worker.setEnabled(request.enabled());
        if (request.password()  != null) worker.setPassword(passwordEncoder.encode(request.password()));
        if (request.pin()       != null) worker.setPin(passwordEncoder.encode(request.pin()));

        User updated = userRepository.save(worker);
        log.info("Worker updated: {} by manager: {}", updated.getEmail(), manager.getEmail());

        return UserResponse.from(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorker(UUID workerId, UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> AuthException.notFound("Manager not found"));

        User worker = userRepository.findById(workerId)
                .orElseThrow(() -> AuthException.notFound("Worker not found"));

        if (!worker.getCompany().getId().equals(manager.getCompany().getId())) {
            throw AuthException.forbidden("Worker does not belong to your company");
        }

        // Soft delete — disable the account rather than removing the record
        worker.setEnabled(false);
        userRepository.save(worker);
        log.info("Worker deactivated: {} by manager: {}", worker.getEmail(), manager.getEmail());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.notFound("User not found"));

        // Verify current password against the stored BCrypt hash
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw AuthException.unauthorized("Current password is incorrect");
        }

        // Reject "change" to the same password — pointless and prevents accidental no-ops
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw AuthException.badRequest("New password must be different from the current one");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        log.info("Password changed for user: {}", user.getEmail());

        // issueTokensFor revokes ALL existing refresh tokens (other devices die)
        // and issues a fresh access+refresh pair so this device stays logged in.
        // Frontend should overwrite its stored tokens with these.
        return authService.issueTokensFor(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestEmailChangeResponse requestEmailChange(UUID userId, RequestEmailChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.notFound("User not found"));

        String newEmail = request.newEmail().trim().toLowerCase();

        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw AuthException.badRequest("New email is the same as the current one");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw AuthException.conflict("Email is already taken");
        }

        Company company = user.getCompany();
        boolean companyVerified = "VERIFIED".equals(company.getVerificationStatus());

        // ── UNVERIFIED path: change immediately ──────────────────────────────
        if (!companyVerified) {
            user.setEmail(newEmail);
            userRepository.save(user);
            log.info("Email changed immediately (company UNVERIFIED): user={} → {}", userId, newEmail);

            // For a manager we also rotate the company verification token and
            // resend the link to the new address — old link becomes useless.
            if (user.getRole() == Role.MANAGER) {
                String newToken = UUID.randomUUID().toString();
                company.setVerificationToken(newToken);
                company.setVerificationTokenExpiry(LocalDateTime.now().plusHours(verificationTokenExpiryHours));
                companyRepository.save(company);
                emailService.sendVerificationEmail(newEmail, newToken);
            }

            // Reissue tokens so the JWT email claim and other-device sessions stay consistent.
            return RequestEmailChangeResponse.changed(authService.issueTokensFor(user));
        }

        // ── VERIFIED path: two-factor — link to OLD email + code to NEW email ──
        // Both must be presented together (token from URL + code from input) to apply.
        String token = UUID.randomUUID().toString();
        String code  = generateNumericCode(6);

        emailChangeCodeRepository.deleteByUserId(userId); // wipe any previous pending challenge
        emailChangeCodeRepository.save(EmailChangeCode.builder()
                .userId(userId)
                .token(token)
                .codeHash(passwordEncoder.encode(code))
                .newEmail(newEmail)
                .expiresAt(LocalDateTime.now().plusMinutes(emailChangeCodeExpiryMinutes))
                .build());

        emailService.sendEmailChangeLink(user.getEmail(), token, newEmail);
        emailService.sendEmailChangeCode(newEmail, code);
        log.info("Email change challenge issued for user={} → {} (link to old, code to new)", userId, newEmail);

        return RequestEmailChangeResponse.codeSent();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse confirmEmailChange(UUID userId, ConfirmEmailChangeRequest request) {
        // Lookup by token (from the link in the OLD email). The token alone proves
        // control of the old address; the code (verified below) proves the new one.
        EmailChangeCode pending = emailChangeCodeRepository.findByToken(request.token())
                .orElseThrow(() -> AuthException.badRequest("Invalid or already-used confirmation link"));

        // Bind the challenge to the authenticated user — prevents using someone
        // else's intercepted token while logged in as a different account.
        if (!pending.getUserId().equals(userId)) {
            throw AuthException.forbidden("This confirmation link does not belong to your account");
        }

        if (LocalDateTime.now().isAfter(pending.getExpiresAt())) {
            emailChangeCodeRepository.delete(pending);
            throw AuthException.badRequest("Confirmation has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(request.code(), pending.getCodeHash())) {
            throw AuthException.unauthorized("Invalid code");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> AuthException.notFound("User not found"));

        // Re-check uniqueness — someone else could have grabbed this email since
        // the challenge was issued.
        if (userRepository.existsByEmail(pending.getNewEmail())
                && !user.getEmail().equals(pending.getNewEmail())) {
            emailChangeCodeRepository.delete(pending);
            throw AuthException.conflict("Email is no longer available");
        }

        user.setEmail(pending.getNewEmail());
        userRepository.save(user);
        emailChangeCodeRepository.delete(pending);

        log.info("Email changed via two-factor confirmation: user={} → {}", userId, pending.getNewEmail());

        // Issue fresh tokens — JWT contains the email claim, must be re-issued.
        return authService.issueTokensFor(user);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    // Random N-digit numeric code (zero-padded). Uses SecureRandom — codes must
    // not be predictable since they grant the ability to change account email.
    private String generateNumericCode(int digits) {
        int max = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", random.nextInt(max));
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
