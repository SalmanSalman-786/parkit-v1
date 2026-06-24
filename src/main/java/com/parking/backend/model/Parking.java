package com.parking.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "parkings")
public class Parking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name;
    private String location;

    private double latitude;
    private double longitude;

    private int twoWheelerCapacity;
    private int twoWheelerAvailable;

    private int fourWheelerCapacity;
    private int fourWheelerAvailable;

    private String imageUrl;
    private String description;

    private boolean active = true;

    private int graceMinutes = 30;

    private int waitlistReservationMinutes = 15;

    private int activeCars;

    private int activeBikes;

    private int bookingCapacityCars = 2;

    private int bookingCapacityBikes = 2;

    private double bikeHourlyRate = 10;

    private double carHourlyRate = 20;

    public Integer getGraceMinutes() {
        return graceMinutes;
    }

    public void setGraceMinutes(Integer graceMinutes) {
        this.graceMinutes = graceMinutes;
    }
}