package com.grainflow.auth.service;

import com.grainflow.auth.dto.request.*;
import com.grainflow.auth.dto.response.AuthResponse;
import com.grainflow.auth.dto.response.ValidateTokenResponse;
import com.grainflow.auth.entity.User;

public interface AuthService {

    // Register a new manager and create their company — sends verification email, returns tokens for immediate login
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

    // Verify email with the token sent after registration
    AuthResponse verifyEmail(String token);

    // Resend verification email to the given address
    void resendVerification(String email);

    // "I forgot my password" — issue a one-time reset link to the given email
    // if it belongs to a verified company. Always silent on failure
    // (anti-enumeration: never reveal whether the email exists).
    void forgotPassword(ForgotPasswordRequest request);

    // Complete the reset using the token from the email + a new password.
    // Validates token, applies password, marks token used, revokes all refresh tokens.
    void resetPassword(ResetPasswordRequest request);

    // Revoke all of this user's refresh tokens and issue a fresh access+refresh pair.
    // Used by login/register/refresh/verify and by password change to keep the
    // current session alive after revoking everything else.
    AuthResponse issueTokensFor(User user);
}
