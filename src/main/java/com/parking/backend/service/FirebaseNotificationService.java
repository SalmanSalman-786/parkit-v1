package com.parking.backend.service;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.parking.backend.repository.UserRepository;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.parking.backend.model.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Async;

@Service
@RequiredArgsConstructor
public class FirebaseNotificationService {

        private final UserRepository userRepository;

        private static final Logger log = LoggerFactory.getLogger(FirebaseNotificationService.class);

        @Async
        public void sendNotification(
                        String fcmToken,
                        String title,
                        String body) {

                try {

                        Message message = Message.builder()
                                        .setToken(fcmToken)
                                        .setNotification(
                                                        Notification.builder()
                                                                        .setTitle(title)
                                                                        .setBody(body)
                                                                        .build())
                                        .build();

                        String response = FirebaseMessaging
                                        .getInstance()
                                        .send(message);

                        log.debug("Firebase notification sent successfully.");

                        log.info("Push notification sent.");

                } catch (FirebaseMessagingException e) {

                        log.error(
                                        "Firebase error: {}",
                                        e.getMessagingErrorCode());

                        log.error(
                                        "Firebase notification failed.",
                                        e);

                } catch (Exception e) {

                        log.error(
                                        "Notification sending failed.",
                                        e);

                }
        }

        public void sendAnnouncementToAllUsers(
                        String title,
                        String message) {

                List<User> users = userRepository.findAll();

                for (User user : users) {

                        if (user.getFcmToken() == null ||
                                        user.getFcmToken().isBlank()) {
                                continue;
                        }

                        try {

                                sendNotification(
                                                user.getFcmToken(),
                                                title,
                                                message);

                        } catch (Exception e) {

                                log.warn(
                                                "Failed to send announcement to user {}",
                                                user.getId());
                        }
                }
        }
}