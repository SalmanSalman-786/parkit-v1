package com.parking.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebhookResult {

    private boolean success;

    private boolean retry;

    private String message;

}