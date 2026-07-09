package com.parking.backend.mapper;

import com.parking.backend.dto.AnnouncementResponse;
import com.parking.backend.model.Announcement;

public class AnnouncementMapper {

    private AnnouncementMapper() {
    }

    public static AnnouncementResponse toResponse(
            Announcement announcement) {

        AnnouncementResponse dto =
                new AnnouncementResponse();

        dto.setId(announcement.getId());
        dto.setTitle(announcement.getTitle());
        dto.setMessage(announcement.getMessage());
        dto.setActive(announcement.isActive());
        dto.setCreatedAt(announcement.getCreatedAt());

        return dto;
    }
}