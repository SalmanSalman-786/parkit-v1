package com.parking.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import com.parking.backend.dto.AnnouncementResponse;
import com.parking.backend.model.Announcement;
import com.parking.backend.service.AnnouncementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    // USER APP
    @GetMapping("/api/announcements")
    public List<AnnouncementResponse> getAnnouncements() {

        return announcementService.getAnnouncements();
    }

    // ADMIN DASHBOARD
    @PostMapping("/api/admin/announcements")
    public Announcement createAnnouncement(
            @RequestBody Map<String, String> request) {

        return announcementService.createAnnouncement(
                request.get("title"),
                request.get("message"));
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    public String deleteAnnouncement(
            @PathVariable String id) {

        announcementService.deleteAnnouncement(id);

        return "Announcement deleted";
    }

    @PutMapping("/api/admin/announcements/{id}/disable")
    public String disableAnnouncement(
            @PathVariable String id) {

        announcementService.disableAnnouncement(id);

        return "Announcement disabled";
    }

    @GetMapping("/api/admin/announcements")
public List<AnnouncementResponse> getAllAnnouncements() {

    return announcementService.getAllAnnouncements();
}

    @PutMapping("/api/admin/announcements/{id}/enable")
    public String enableAnnouncement(
            @PathVariable String id) {

        announcementService.enableAnnouncement(id);

        return "Announcement enabled";
    }

}