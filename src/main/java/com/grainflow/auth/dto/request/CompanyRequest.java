package com.grainflow.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

// Company data provided during manager registration
public record CompanyRequest(

        @NotBlank(message = "Company name is required")
        String name,

        String address,

        String phone
) {}
