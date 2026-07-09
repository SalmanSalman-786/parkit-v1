package com.parking.backend.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class AnnouncementResponse {

    private String id;

    private String title;

    private String message;

    private boolean active;

    private LocalDateTime createdAt;
}