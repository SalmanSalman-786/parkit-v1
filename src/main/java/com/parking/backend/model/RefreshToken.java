package com.parking.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false, length = 500)
    private String token;

    private String userId;

    private LocalDateTime expiryDate;

    private boolean revoked = false;

    private LocalDateTime createdAt;
}