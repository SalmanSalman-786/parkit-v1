package com.parking.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.parking.backend.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(
            String userId,
            Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    List<Notification> findByUserIdAndIsReadTrueOrderByCreatedAtAsc(String userId);

    long countByUserId(String userId);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM notifications
            WHERE id IN (

                SELECT id
                FROM notifications
                WHERE user_id = :userId
                  AND is_read = true
                ORDER BY created_at ASC
                LIMIT :limit

            )
            """, nativeQuery = true)
    void deleteOldestReadNotifications(
            @Param("userId") String userId,
            @Param("limit") long limit);

    long deleteByUserIdAndIsReadTrue(String userId);

    long deleteByIdAndUserId(String id, String userId);

    @Transactional
    @Modifying
    long deleteByCreatedAtBefore(LocalDateTime dateTime);

}