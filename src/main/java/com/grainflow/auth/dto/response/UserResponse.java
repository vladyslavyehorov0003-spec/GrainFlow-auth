package com.grainflow.auth.dto.response;

import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

// Public user representation — never exposes password or PIN
public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String employeeId,
        Role role,
        UUID companyId,
        String companyName,
        boolean enabled,
        LocalDateTime createdAt
) {
    // Maps a User entity to a safe response DTO
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getEmployeeId(),
                user.getRole(),
                user.getCompany().getId(),
                user.getCompany().getName(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
