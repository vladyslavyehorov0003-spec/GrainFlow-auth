package com.grainflow.auth.repository;

import com.grainflow.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Wipe any pending tokens for this user before issuing a new one,
    // so each "forgot password" request invalidates the previous link.
    void deleteByUserId(UUID userId);
}
