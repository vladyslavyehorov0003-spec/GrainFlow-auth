package com.grainflow.auth.controller;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.ApiResponse;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.User;
import com.grainflow.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {

    private final AuthService authService;

    // Register a new manager with company details
    @PostMapping("/register")
    @Operation(summary = "Register manager", description = "Creates a new manager account and a company")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Manager registered successfully"));
    }

    // Login via email + password — managers (browser) and workers (mobile phone)
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate via email and password — managers and workers")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    // Terminal login via employeeId + PIN — reserved for physical terminals at zone entrances
    @PostMapping("/terminal-login")
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

    // Internal endpoint — used by other microservices to validate a JWT token.
    // JwtAuthFilter already verified the signature and loaded the user into the SecurityContext.
    // If the token was invalid or missing, the filter leaves the principal null — we return valid=false.
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Used by other microservices to verify token validity")
    public ResponseEntity<ApiResponse<ValidateTokenResponse>> validate(
            @AuthenticationPrincipal User currentUser) {
        ValidateTokenResponse response = authService.validate(currentUser);
        String message = response.valid() ? "Token is valid" : "Token is missing or invalid";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }
}
