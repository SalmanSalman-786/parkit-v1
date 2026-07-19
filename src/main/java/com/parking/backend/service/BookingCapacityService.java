package com.parking.backend.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.parking.backend.model.BookingCapacity;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingCapacityRepository;
import com.parking.backend.repository.ParkingRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BookingCapacityService {

    private final BookingCapacityRepository bookingCapacityRepository;
    private final ParkingRepository parkingRepository;

    public BookingCapacityService(
            BookingCapacityRepository bookingCapacityRepository,
            ParkingRepository parkingRepository) {

        this.bookingCapacityRepository = bookingCapacityRepository;
        this.parkingRepository = parkingRepository;
    }

    private static final Logger log = LoggerFactory.getLogger(BookingCapacityService.class);

    @Transactional
    public BookingCapacity getOrCreateCapacity(
            String parkingId,
            LocalDate bookingDate,
            String vehicleType) {

        if (!"TWO_WHEELER".equals(vehicleType)
                && !"FOUR_WHEELER".equals(vehicleType)) {

            throw new RuntimeException("Invalid vehicle type");
        }

        BookingCapacity existing = bookingCapacityRepository
                .findByParkingIdAndBookingDateAndVehicleType(
                        parkingId,
                        bookingDate,
                        vehicleType)
                .orElse(null);

        if (existing != null) {
            return existing;
        }

        Parking parking = parkingRepository.findById(parkingId)
                .orElseThrow(() -> new RuntimeException("Parking not found"));

        BookingCapacity capacity = new BookingCapacity();

        capacity.setParkingId(parkingId);
        capacity.setBookingDate(bookingDate);
        capacity.setVehicleType(vehicleType);

        if ("TWO_WHEELER".equals(vehicleType)) {
            capacity.setBookingCapacity(parking.getBookingCapacityBikes());
        } else {
            capacity.setBookingCapacity(parking.getBookingCapacityCars());
        }

        capacity.setBookedCount(0);

        try {

            return bookingCapacityRepository.saveAndFlush(capacity);

        } catch (org.springframework.dao.DataIntegrityViolationException e) {

            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Another booking is creating today's capacity. Please try again.");
        }
    }

    @Transactional
    public BookingCapacity reserveSlot(
            String parkingId,
            LocalDate bookingDate,
            String vehicleType) {

        if (!"TWO_WHEELER".equals(vehicleType)
                && !"FOUR_WHEELER".equals(vehicleType)) {

            throw new RuntimeException("Invalid vehicle type");
        }

        getOrCreateCapacity(
                parkingId,
                bookingDate,
                vehicleType);

        BookingCapacity capacity = bookingCapacityRepository
                .lockCapacity(parkingId, bookingDate, vehicleType)
                .orElseThrow(() -> new RuntimeException("Booking capacity not found"));

        log.debug(
                "Reserving slot: parking={}, date={}, vehicleType={}",
                parkingId,
                bookingDate,
                vehicleType);

        if (capacity.getBookedCount() >= capacity.getBookingCapacity()) {
            throw new RuntimeException("Booking slots full");
        }

        capacity.setBookedCount(
                capacity.getBookedCount() + 1);

        

        return bookingCapacityRepository.save(capacity);

    }

    @Transactional
    public void releaseSlot(
            String parkingId,
            LocalDate bookingDate,
            String vehicleType) {

        BookingCapacity capacity = bookingCapacityRepository
                .lockCapacity(
                        parkingId,
                        bookingDate,
                        vehicleType)
                .orElse(null);

        if (capacity == null) {
            return;
        }

        if (capacity.getBookedCount() > 0) {
            capacity.setBookedCount(
                    capacity.getBookedCount() - 1);

            bookingCapacityRepository.save(capacity);
        }
    }
}