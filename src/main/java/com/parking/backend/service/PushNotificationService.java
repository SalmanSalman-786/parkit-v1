package com.parking.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;

import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    public void sendPushNotification(
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

            System.out.println(
                    "🔥 Notification sent: " + response);

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}