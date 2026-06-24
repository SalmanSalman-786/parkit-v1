package com.parking.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    RealtimeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendDashboardUpdate(Object data) {
        messagingTemplate.convertAndSend("/topic/dashboard", data);
    }
}