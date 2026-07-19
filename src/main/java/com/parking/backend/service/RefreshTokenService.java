package com.parking.backend.service;

import com.parking.backend.model.RefreshToken;
import com.parking.backend.repository.RefreshTokenRepository;

import jakarta.transaction.Transactional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RefreshTokenService {

        private final RefreshTokenRepository refreshTokenRepository;

        RefreshTokenService(
                        RefreshTokenRepository refreshTokenRepository) {

                this.refreshTokenRepository = refreshTokenRepository;
        }

        private static final Logger log =
        LoggerFactory.getLogger(RefreshTokenService.class);

        public RefreshToken createRefreshToken(
                        String userId) {

                RefreshToken token = new RefreshToken();

                token.setUserId(userId);

                token.setToken(
                                UUID.randomUUID().toString()
                                                + UUID.randomUUID().toString());

                token.setCreatedAt(
                                LocalDateTime.now());

                token.setExpiryDate(
                                LocalDateTime.now().plusDays(7));

                token.setRevoked(false);

                return refreshTokenRepository.save(token);
        }

        public boolean isValid(
                        RefreshToken token) {

                return !token.isRevoked()
                                && token.getExpiryDate()
                                                .isAfter(LocalDateTime.now());
        }

        public Optional<RefreshToken> findByToken(
                        String token) {

                return refreshTokenRepository.findByToken(token);
        }


        @Transactional
        public void revoke(
                        RefreshToken token) {

                token.setRevoked(true);

                refreshTokenRepository.save(token);
        }

        @Transactional
        public void deleteToken(String token) {

                log.debug("Deleting refresh token.");

                refreshTokenRepository.deleteByToken(token);

                
        }

        @Scheduled(cron = "0 0 2 * * ?")
        @Transactional
        public void cleanupExpiredTokens() {

                refreshTokenRepository.deleteByExpiryDateBefore(
                                LocalDateTime.now());

                refreshTokenRepository.deleteByRevokedTrue();

                log.info("Expired and revoked refresh tokens cleaned.");
        }

        public void revokeAllUserTokens(String userId) {

                refreshTokenRepository.revokeAllByUserId(userId);
        }
}