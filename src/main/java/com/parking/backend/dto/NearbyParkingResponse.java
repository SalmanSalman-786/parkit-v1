package com.parking.backend.dto;

import com.parking.backend.model.Parking;
import java.util.List;

public class NearbyParkingResponse {

    private List<Parking> parkings;
    private boolean fallback;
    private String message;

    public NearbyParkingResponse() {
    }

    public NearbyParkingResponse(List<Parking> parkings, boolean fallback, String message) {
        this.parkings = parkings;
        this.fallback = fallback;
        this.message = message;
    }

    public List<Parking> getParkings() {
        return parkings;
    }

    public void setParkings(List<Parking> parkings) {
        this.parkings = parkings;
    }

    public boolean isFallback() {
        return fallback;
    }

    public void setFallback(boolean fallback) {
        this.fallback = fallback;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}