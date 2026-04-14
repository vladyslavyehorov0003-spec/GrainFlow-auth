package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Terminal login — used on physical terminals placed at zone entrances.
// Workers enter their employeeId + PIN directly on the device.
// Reserved for future terminal integration.
public record WorkerLoginRequest(

        @NotBlank(message = "Employee ID is required")
        String employeeId,

        @NotBlank(message = "PIN is required")
        @Size(min = 4, max = 6, message = "PIN must be between 4 and 6 characters")
        String pin
) {}
