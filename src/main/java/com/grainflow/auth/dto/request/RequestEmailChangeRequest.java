package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestEmailChangeRequest(

        @NotBlank(message = "New email is required")
        @Email(message = "Invalid email format")
        String newEmail
) {
    public RequestEmailChangeRequest {
        if (newEmail != null) newEmail = newEmail.trim().toLowerCase();
    }
}
