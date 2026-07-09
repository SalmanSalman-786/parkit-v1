package com.parking.backend.dto;

import lombok.Data;

@Data
public class WebhookPaymentDto {

    private String event;

    private String paymentId;

    private String orderId;

    private String receipt;

    private String status;

    private double amount;

    private String paymentDescription;
}