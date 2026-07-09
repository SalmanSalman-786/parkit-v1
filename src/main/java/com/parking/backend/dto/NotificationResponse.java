package com.parking.backend.dto;

import java.time.LocalDateTime;

import com.parking.backend.model.NotificationType;

import lombok.Data;

@Data
public class NotificationResponse {

    private String id;

    private String title;

    private String message;

    private NotificationType type;

    private boolean isRead;

    private LocalDateTime createdAt;
}