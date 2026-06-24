package com.parking.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleSlotDto {

    private String bookingId;

    private String vehicleNumber;

    private String phoneNumber;

    private String status;

    private String paymentMode;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}