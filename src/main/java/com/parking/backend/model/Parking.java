package com.parking.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "parkings")
public class Parking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Version
    private Long version;

    @NotBlank(message = "Parking name is required")
    private String name;

    @NotBlank(message = "Location is required")
    private String location;

    @DecimalMin(value = "-90.0", message = "Invalid latitude")
    @DecimalMax(value = "90.0", message = "Invalid latitude")
    private double latitude;

    @DecimalMin(value = "-180.0", message = "Invalid longitude")
    @DecimalMax(value = "180.0", message = "Invalid longitude")
    private double longitude;

    @PositiveOrZero(message = "Bike capacity cannot be negative")
    private int twoWheelerCapacity;
    private int twoWheelerAvailable;

    @PositiveOrZero(message = "Car capacity cannot be negative")
    private int fourWheelerCapacity;
    private int fourWheelerAvailable;

    private String imageUrl;
    private String description;

    private boolean active = true;

    private LocalDate bookingWindowStart;

    private LocalDate bookingWindowEnd;

    private int graceMinutes = 30;

    private int waitlistReservationMinutes = 15;

    private int activeCars;

    private int activeBikes;

    @PositiveOrZero(message = "Booking car capacity cannot be negative")
    private int bookingCapacityCars = 2;

    @PositiveOrZero(message = "Booking bike capacity cannot be negative")
    private int bookingCapacityBikes = 2;

    @Positive(message = "Bike rate must be greater than 0")
    private double bikeHourlyRate = 10;

    @Positive(message = "Car rate must be greater than 0")
    private double carHourlyRate = 20;

    @PositiveOrZero(message = "Bike assurance deposit cannot be negative")
    private double bikeAssuranceDeposit = 50;

    @PositiveOrZero(message = "Car assurance deposit cannot be negative")
    private double carAssuranceDeposit = 100;

    public Integer getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(Integer graceMinutes) {
        this.graceMinutes = graceMinutes;
    }
}