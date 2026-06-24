package com.parking.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RevenueSummaryResponse {

    private LocalDate date;

    private double totalRevenue;

    private double onlineRevenue;

    private double cashRevenue;

    private double fineRevenue;

    private double refundAmount;

    private long transactionCount;

    private double netRevenue;

    public double getNetRevenue() {
        return netRevenue;
    }

    public void setNetRevenue(double netRevenue) {
        this.netRevenue = netRevenue;
    }
}