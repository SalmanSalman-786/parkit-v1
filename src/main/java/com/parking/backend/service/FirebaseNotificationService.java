package com.parking.backend.service;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.FirebaseMessagingException;

@Service
public class FirebaseNotificationService {

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
                        System.out.println("FCM Token = " + fcmToken);
                        String response = FirebaseMessaging
                                        .getInstance()
                                        .send(message);

                        System.out.println("Firebase Response = " + response);

                        System.out.println("Notification sent successfully");

                } catch (FirebaseMessagingException e) {

                        System.out.println("Firebase Error Code = "
                                        + e.getMessagingErrorCode());

                        System.out.println("Firebase Message = "
                                        + e.getMessage());

                        e.printStackTrace();

                } catch (Exception e) {

                        System.out.println(
                                        "Notification failed: " + e.getMessage());

                        e.printStackTrace();
                }
        }
}