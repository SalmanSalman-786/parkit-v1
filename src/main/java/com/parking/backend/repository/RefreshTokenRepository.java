package com.parking.backend.repository;

import com.parking.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    @Transactional
    void deleteByToken(String token);

    void deleteByExpiryDateBefore(
            LocalDateTime now);

    @Modifying
    @Transactional
    @Query("""
            UPDATE RefreshToken r
            SET r.revoked = true
            WHERE r.userId = :userId
            AND r.revoked = false
            """)
    void revokeAllByUserId(String userId);

    @Transactional
    void deleteByUserId(String userId);

    @Transactional
    void deleteByRevokedTrue();
}