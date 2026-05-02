package com.grainflow.auth.repository;

import com.grainflow.auth.entity.EmailChangeCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeCodeRepository extends JpaRepository<EmailChangeCode, UUID> {

    // Used at confirm time — frontend hits the link and forwards the token.
    Optional<EmailChangeCode> findByToken(String token);

    // Used when re-requesting a change — wipe any pending challenge for this user
    // before issuing a fresh token+code pair.
    void deleteByUserId(UUID userId);
}
