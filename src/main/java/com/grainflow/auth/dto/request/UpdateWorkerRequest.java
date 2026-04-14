package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// All fields are optional — null means "do not update this field"
public record UpdateWorkerRequest(

        String firstName,

        String lastName,

        // Validated only when provided
        @Email(message = "Invalid email format")
        String email,

        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @Size(min = 4, max = 6, message = "PIN must be between 4 and 6 characters")
        String pin
) {}
