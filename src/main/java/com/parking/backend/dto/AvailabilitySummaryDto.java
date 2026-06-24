package com.parking.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class AvailabilitySummaryDto {

    private String parkingId;
    private String parkingName;

    private int totalCapacity;

    private long bookedCount;

    private long activeCount;

    private long availableCount;

    private String status;

    private List<VehicleSlotDto> vehicles;
}