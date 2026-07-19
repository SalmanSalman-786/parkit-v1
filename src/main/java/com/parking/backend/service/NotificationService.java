package com.parking.backend.service;

import java.time.LocalDateTime;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.backend.dto.NotificationResponse;
import com.parking.backend.dto.PageResponse;
import com.parking.backend.mapper.NotificationMapper;
import com.parking.backend.model.Notification;
import com.parking.backend.model.NotificationType;
import com.parking.backend.model.User;
import com.parking.backend.repository.NotificationRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.util.PageResponseUtil;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.scheduling.annotation.Scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    private final UserRepository userRepository;

    private final FirebaseNotificationService firebaseNotificationService;

    private static final int MAX_NOTIFICATIONS = 100;

    private static final Logger log =
        LoggerFactory.getLogger(NotificationService.class);

    @Transactional
    public void sendAlert(
            String userId,
            String title,
            String message,
            NotificationType type) {

        User user = userRepository
                .findById(userId)
                .orElse(null);

        if (user == null) {
            return;
        }

        Notification notification = new Notification();

        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);

        keepLatest100Notifications(userId);

        if (user.getFcmToken() != null &&
                !user.getFcmToken().isBlank()) {

            firebaseNotificationService.sendNotification(
                    user.getFcmToken(),
                    title,
                    message);
        }
    }

    private void keepLatest100Notifications(String userId) {

        long count = notificationRepository.countByUserId(userId);

        if (count <= MAX_NOTIFICATIONS) {
            return;
        }

        long extra = count - MAX_NOTIFICATIONS;

        notificationRepository.deleteOldestReadNotifications(
                userId,
                extra);
    }

    public PageResponse<NotificationResponse> getUserNotifications(
            String userId,
            int page,
            int size) {

        page = Math.max(page, 0);

        size = Math.max(size, 1);

        size = Math.min(size, MAX_NOTIFICATIONS);

        Pageable pageable = PageRequest.of(page, size);

        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                pageable);

        Page<NotificationResponse> pageResponse = notifications.map(NotificationMapper::toResponse);

        return PageResponseUtil.from(pageResponse);
    }

    public long getUnreadCount(String userId) {

        return notificationRepository
                .countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(String notificationId, String userId) {

        Notification notification = notificationRepository
                .findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        notification.setRead(true);

        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {

        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);

        for (Notification notification : notifications) {

            if (!notification.isRead()) {

                notification.setRead(true);
            }
        }

        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void deleteNotification(String notificationId, String userId) {

        long deleted = notificationRepository
                .deleteByIdAndUserId(notificationId, userId);

        if (deleted == 0) {
            throw new RuntimeException("Notification not found");
        }
    }

    @Transactional
    public long clearReadNotifications(String userId) {

        return notificationRepository
                .deleteByUserIdAndIsReadTrue(userId);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void deleteOldNotifications() {

        log.info("Notification cleanup started.");

        long deleted = notificationRepository.deleteByCreatedAtBefore(
                LocalDateTime.now().minusDays(30));

        log.info("Deleted {} old notifications.", deleted);
    }
}