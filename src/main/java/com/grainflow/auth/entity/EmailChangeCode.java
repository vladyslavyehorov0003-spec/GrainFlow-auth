package com.grainflow.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_change_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailChangeCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    // UUID embedded in the link sent to the OLD email (proof you control it).
    // Stored plaintext — 122 bits of entropy is brute-force-resistant.
    @Column(nullable = false, unique = true)
    private String token;

    // BCrypt hash of the 6-digit code sent to the NEW email (proof you own it).
    // Hashed because 6 digits = ~20 bits of entropy, vulnerable to brute force on a stolen DB.
    @Column(nullable = false)
    private String codeHash;

    // Target email the user wants to switch to.
    @Column(nullable = false)
    private String newEmail;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
