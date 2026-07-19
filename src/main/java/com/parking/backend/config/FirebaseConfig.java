package com.parking.backend.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import jakarta.annotation.PostConstruct;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() {

        try {

            if (!FirebaseApp.getApps().isEmpty()) {
                return;
            }

            String firebaseJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

            if (firebaseJson == null || firebaseJson.isBlank()) {
                throw new IllegalStateException(
                        "FIREBASE_SERVICE_ACCOUNT_JSON environment variable is missing.");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(
                            GoogleCredentials.fromStream(
                                    new ByteArrayInputStream(
                                            firebaseJson.getBytes(StandardCharsets.UTF_8))))
                    .build();

            FirebaseApp.initializeApp(options);

            System.out.println("Firebase initialized successfully.");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}