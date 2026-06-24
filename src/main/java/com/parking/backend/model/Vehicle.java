package com.parking.backend.model;

import jakarta.persistence.Embeddable;
import lombok.Data;

@Data
@Embeddable
public class Vehicle {

    private String vehicleNumber;
    private String type;
}