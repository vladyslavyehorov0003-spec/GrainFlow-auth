package com.grainflow.auth.controller;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.ApiResponse;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {

    private final AuthService authService;

    // Register a new manager — sends verification email, no tokens returned yet
    @PostMapping("/register")
    @Operation(summary = "Register manager", description = "Creates a new manager account and sends a verification email")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authResponse, "Registration successful. Please check your email to verify your account."));
    }

    // Login via email + password — managers (browser) and workers (mobile phone)
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate via email and password — managers and workers")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    // Verify email using the token from the verification email link
    @PostMapping("/verify")
    @Operation(summary = "Verify email", description = "Verify email address using the token sent after registration")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@RequestParam String token) {
        AuthResponse response = authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(response, "Email verified successfully"));
    }

    // Resend verification email
    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Send a new verification email to the given address")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.success(null, "Verification email sent"));
    }

    // Forgot password — always returns 200, even if the email is unknown,
    // so attackers can't enumerate registered emails.
    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password",
               description = "Sends a one-time reset link to the given email if the account exists. " +
                             "Always returns 200 regardless — never reveals whether the email is registered.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an account exists for that email, a reset link has been sent."));
    }

    // Reset password — completes the flow with the token from the email and a new password.
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password",
               description = "Completes the forgot-password flow: validates the one-time token, " +
                             "applies the new password, and revokes every existing session.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset. Please log in with the new password."));
    }

    // Terminal login via employeeId + PIN — reserved for physical terminals at zone entrances
    @PostMapping("/terminal-login")
    @Hidden
    @Operation(summary = "Terminal login", description = "Authenticate via employeeId and PIN on a physical terminal")
    public ResponseEntity<ApiResponse<AuthResponse>> terminalLogin(@Valid @RequestBody WorkerLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.terminalLogin(request), "Terminal login successful"));
    }

    // Refresh access token using a valid refresh token
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get a new access token using a refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request), "Token refreshed successfully"));
    }

    // Logout — invalidates the refresh token
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revoke refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    // Internal endpoint — used by other microservices to validate a JWT token
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Used by other microservices to verify token validity")
    public ResponseEntity<ApiResponse<ValidateTokenResponse>> validate(
            @AuthenticationPrincipal User currentUser) {
        ValidateTokenResponse response = authService.validate(currentUser);
        String message = response.valid() ? "Token is valid" : "Token is missing or invalid";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }
}
