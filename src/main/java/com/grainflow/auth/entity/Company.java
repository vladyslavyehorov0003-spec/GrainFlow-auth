package com.grainflow.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Possible values: UNVERIFIED, VERIFIED

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String address;

    @Column
    private String phone;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "VARCHAR(16) DEFAULT 'INACTIVE'")
    private String subscriptionStatus = "INACTIVE";

    @Builder.Default
    @Column(nullable = false, columnDefinition = "VARCHAR(16) DEFAULT 'UNVERIFIED'")
    private String verificationStatus = "UNVERIFIED";

    @Column
    private String verificationToken;

    @Column
    private LocalDateTime verificationTokenExpiry;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
