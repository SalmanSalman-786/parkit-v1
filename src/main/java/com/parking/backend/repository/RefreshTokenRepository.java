package com.parking.backend.repository;

import com.parking.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    @Transactional
    void deleteByToken(String token);

     void deleteByExpiryDateBefore(
            LocalDateTime now);
}