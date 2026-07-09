package com.parking.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    RealtimeService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendDashboardUpdate(Object data) {
        messagingTemplate.convertAndSend("/topic/dashboard", data);
    }

    public void sendExitPaymentSuccess(
            String bookingId,
            String vehicleNumber) {

        Map<String, Object> data = new HashMap<>();

        data.put("type", "EXIT_PAYMENT_SUCCESS");

        data.put("bookingId", bookingId);

        data.put("vehicleNumber", vehicleNumber);

        messagingTemplate.convertAndSend(
                "/topic/dashboard",
                (Object) data);
    }
}