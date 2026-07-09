package com.parking.backend.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Embeddable
public class Vehicle {


    private String vehicleId;

    @NotBlank(message = "Vehicle number is required")
    private String vehicleNumber;

    @NotBlank(message = "Vehicle type is required")
    @Pattern(
            regexp = "TWO_WHEELER|FOUR_WHEELER",
            message = "Invalid vehicle type")
    private String type;

    
}