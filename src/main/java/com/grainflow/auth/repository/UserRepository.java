package com.grainflow.auth.repository;

import com.grainflow.auth.entity.Role;
import com.grainflow.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Always fetch company alongside user to avoid LazyInitializationException
    @EntityGraph(attributePaths = "company")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "company")
    Optional<User> findByEmployeeId(String employeeId);

    @EntityGraph(attributePaths = "company")
    Optional<User> findById(UUID id);

    // Fetch all users of a specific role within a company
    @EntityGraph(attributePaths = "company")
    List<User> findAllByCompanyIdAndRole(UUID companyId, Role role);

    boolean existsByEmail(String email);

    boolean existsByEmployeeId(String employeeId);
}
