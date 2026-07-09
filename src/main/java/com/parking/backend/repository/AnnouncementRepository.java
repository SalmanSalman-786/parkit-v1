package com.parking.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parking.backend.model.Announcement;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, String> {

    List<Announcement> findByActiveTrueOrderByCreatedAtDesc();

    List<Announcement> findAllByOrderByCreatedAtDesc();

    
}