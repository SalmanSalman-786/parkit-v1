package com.parking.backend.dto;

import lombok.Data;

@Data
public class ParkingRevenueDto {

    private String parkingId;

    private String parkingName;

    private double revenue;
}