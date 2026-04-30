package com.grainflow.auth.repository;

import com.grainflow.auth.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByName(String name);

    Optional<Company> findByVerificationToken(String verificationToken);
}
