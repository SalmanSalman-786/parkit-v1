package com.parking.backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.parking.backend.dto.AnnouncementResponse;
import com.parking.backend.mapper.AnnouncementMapper;
import com.parking.backend.model.Announcement;
import com.parking.backend.repository.AnnouncementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    private final FirebaseNotificationService firebaseNotificationService;

    public List<AnnouncementResponse> getAnnouncements() {

        return announcementRepository
                .findByActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementMapper::toResponse)
                .toList();
    }

    public Announcement createAnnouncement(
            String title,
            String message) {

        Announcement announcement = new Announcement();

        announcement.setTitle(title);

        announcement.setMessage(message);

        announcement.setCreatedAt(LocalDateTime.now());

        announcement.setActive(true);

        Announcement saved = announcementRepository.save(announcement);

        firebaseNotificationService.sendAnnouncementToAllUsers(
                saved.getTitle(),
                saved.getMessage());

        return saved;
    }

    public void deleteAnnouncement(String id) {

        announcementRepository.deleteById(id);
    }

    public void disableAnnouncement(String id) {

        Announcement announcement = announcementRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));

        announcement.setActive(false);

        announcementRepository.save(announcement);
    }

    public List<AnnouncementResponse> getAllAnnouncements() {

        return announcementRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AnnouncementMapper::toResponse)
                .toList();
    }

    public void enableAnnouncement(String id) {

        Announcement announcement = announcementRepository
                .findById(id)
                .orElseThrow(() -> new RuntimeException("Announcement not found"));

        announcement.setActive(true);

        announcementRepository.save(announcement);
    }
}