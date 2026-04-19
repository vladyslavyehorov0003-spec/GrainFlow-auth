package com.grainflow.auth.repository;

import com.grainflow.auth.dto.request.UserFilterRequest;
import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class UserSpecification {

    public static Specification<User> filter(UUID companyId, UserFilterRequest f) {
        return Specification
                .where(hasCompany(companyId))
                .and(hasRole(Role.WORKER))
                .and(searchMatches(f.search()))
                .and(hasEnabled(f.enabled()));
    }

    // Always scope to the manager's company
    private static Specification<User> hasCompany(UUID companyId) {
        return (root, query, cb) -> cb.equal(root.get("company").get("id"), companyId);
    }

    // Always return only workers, never managers
    private static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    // Search across firstName, lastName, email and employeeId — case-insensitive
    private static Specification<User> searchMatches(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String pattern = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")),  pattern),
                    cb.like(cb.lower(root.get("lastName")),   pattern),
                    cb.like(cb.lower(root.get("email")),      pattern),
                    cb.like(cb.lower(root.get("employeeId")), pattern)
            );
        };
    }

    // Filter by active/inactive status — null means "show all"
    private static Specification<User> hasEnabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? null
                : cb.equal(root.get("enabled"), enabled);
    }
}
