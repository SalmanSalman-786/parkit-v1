package com.parking.backend.dto;

import lombok.Data;

@Data
public class CancelPreviewResponse {

    private double bookingFee;

    private double assuranceDeposit;

    private double bookingFeeRefund;

    private double assuranceDepositRefund;

    private double totalRefund;

    private String message;
}