package com.parking.backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ==========================
    // When
    // ==========================

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // ==========================
    // Who
    // ==========================

    @Column(nullable = false)
    private String userId;

    private String username;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditActorRole role;

    // ==========================
    // What
    // ==========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    // BOOKING / PAYMENT / PARKING / USER...
    private String entityType;

    // Booking ID / Parking ID / etc.
    private String entityId;

    @Column(length = 1000)
    private String description;

    // ==========================
    // Security
    // ==========================

    private String ipAddress;

    @Column(nullable = false)
    private Boolean success;

}