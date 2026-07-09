package com.parking.backend.dto;

import java.util.Map;

public class OperationLookupResponse {

    private String action;
    private String vehicleNumber;
    private String bookingId;
    private Map<String, Object> bookingDetails;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public Map<String, Object> getBookingDetails() {
        return bookingDetails;
    }

    public void setBookingDetails(Map<String, Object> bookingDetails) {
        this.bookingDetails = bookingDetails;
    }
}