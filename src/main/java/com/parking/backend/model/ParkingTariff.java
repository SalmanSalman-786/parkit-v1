package com.parking.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import lombok.Data;

@Data
@Entity
@Table(name = "parking_tariffs", indexes = {
        @Index(name = "idx_tariff_parking", columnList = "parkingId"),
        @Index(name = "idx_tariff_booking_type", columnList = "bookingType"),
        @Index(name = "idx_tariff_vehicle_type", columnList = "vehicleType"),
        @Index(name = "idx_tariff_slab_type", columnList = "slabType")
})
public class ParkingTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    private String parkingId;

    // BOOKING | WALKIN
    @NotBlank
    private String bookingType;

    // TWO_WHEELER | FOUR_WHEELER
    @NotBlank
    private String vehicleType;

    // HOURLY | DAILY
    @NotBlank
    private String slabType;

    // Minutes for hourly slabs, Days for daily slabs
    @Positive
    private int maxValue;

    @Positive
    private double price;
}