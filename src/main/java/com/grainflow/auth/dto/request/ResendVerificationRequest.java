package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank @Email String email
) {
    public ResendVerificationRequest {
        if (email != null) email = email.trim().toLowerCase();
    }
}
