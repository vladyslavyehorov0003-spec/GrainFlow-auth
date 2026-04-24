package com.grainflow.auth.dto.response;

import com.grainflow.auth.entity.Role;

import java.util.UUID;

// Response returned to other microservices when they validate a token
public record ValidateTokenResponse(
        boolean valid,
        UUID userId,
        UUID companyId,
        String email,
        Role role,
        String subscriptionStatus  // ACTIVE / PAST_DUE / CANCELED / INACTIVE
) {}
