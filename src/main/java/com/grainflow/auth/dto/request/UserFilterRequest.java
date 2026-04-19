package com.grainflow.auth.dto.request;

public record UserFilterRequest(
        String search,
        Boolean enabled
) {}