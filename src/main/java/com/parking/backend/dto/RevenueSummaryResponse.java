package com.parking.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RevenueSummaryResponse {

    private LocalDate date;

    // Booking Revenue
    private double bookingFee;

    private double bookingFeeRefund;

    // Assurance Deposit
    private double assuranceDeposit;

    private double assuranceDepositRefund;

    // Other Revenue
    private double walkinRevenue;

    private double fineRevenue;

    // Summary
    private double netRevenue;

    private long transactionCount;
}