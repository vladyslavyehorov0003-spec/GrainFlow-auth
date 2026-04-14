package com.grainflow.auth.service;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.User;

public interface AuthService {

    // Register a new manager and create their company
    AuthResponse register(RegisterRequest request);

    // Login via email + password — managers (browser) and workers (mobile phone)
    AuthResponse login(LoginRequest request);

    // Terminal login via employeeId + PIN — physical terminals at zone entrances
    AuthResponse terminalLogin(WorkerLoginRequest request);

    // Refresh an expired access token using a valid refresh token
    AuthResponse refresh(RefreshTokenRequest request);

    // Invalidate the refresh token (logout)
    void logout(String refreshToken);

    // Build a ValidateTokenResponse from the already-authenticated principal.
    // JwtAuthFilter handles token verification — this just shapes the response.
    ValidateTokenResponse validate(User currentUser);
}
