package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// Request to refresh an expired access token using a valid refresh token
public record RefreshTokenRequest(

        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
