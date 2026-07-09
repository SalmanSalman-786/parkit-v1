package com.parking.backend.dto;

public class PaymentStatus {

    private final boolean captured;
    private final String paymentId;

    public PaymentStatus(boolean captured, String paymentId) {
        this.captured = captured;
        this.paymentId = paymentId;
    }

    public boolean isCaptured() {
        return captured;
    }

    public String getPaymentId() {
        return paymentId;
    }
}