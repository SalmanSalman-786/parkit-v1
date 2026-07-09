package com.parking.backend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.parking.backend.dto.NotificationResponse;
import com.parking.backend.dto.PageResponse;
import com.parking.backend.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<NotificationResponse> getNotifications(

            Authentication authentication,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "20") int size) {

        return notificationService.getUserNotifications(

                authentication.getName(),

                page,

                size);
    }

    @GetMapping("/unread-count")
    public long getUnreadCount(
            Authentication authentication) {

        return notificationService.getUnreadCount(
                authentication.getName());
    }

    @PutMapping("/{id}/read")
    public String markAsRead(
            @PathVariable String id,
            Authentication authentication) {

        notificationService.markAsRead(
                id,
                authentication.getName());

        return "Notification marked as read";
    }

    @PutMapping("/read-all")
    public String markAllAsRead(
            Authentication authentication) {

        notificationService.markAllAsRead(
                authentication.getName());

        return "All notifications marked as read";
    }

    @DeleteMapping("/{id}")
    public String deleteNotification(
            @PathVariable String id,
            Authentication authentication) {

        notificationService.deleteNotification(
                id,
                authentication.getName());

        return "Notification deleted";
    }

    @DeleteMapping("/read")
    public String clearReadNotifications(
            Authentication authentication) {

        long count = notificationService
                .clearReadNotifications(authentication.getName());

        return count + " notifications deleted";
    }
}