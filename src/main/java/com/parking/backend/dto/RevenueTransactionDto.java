package com.parking.backend.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RevenueTransactionDto {

    private String bookingId;

    private String vehicleNumber;

    private LocalDateTime dateTime;

    private String transactionType; // BOOKING,FINE,REFUND

    private String paymentMode; // CASH,ONLINE

    private double amount;

    private String parkingId;
    
    private String parkingName;
}