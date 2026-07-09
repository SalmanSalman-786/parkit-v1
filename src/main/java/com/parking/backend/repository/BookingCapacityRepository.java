package com.parking.backend.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.parking.backend.model.BookingCapacity;

import jakarta.persistence.LockModeType;

public interface BookingCapacityRepository extends JpaRepository<BookingCapacity, String> {

    Optional<BookingCapacity> findByParkingIdAndBookingDateAndVehicleType(
            String parkingId,
            LocalDate bookingDate,
            String vehicleType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM BookingCapacity b
            WHERE b.parkingId = :parkingId
            AND b.bookingDate = :bookingDate
            AND b.vehicleType = :vehicleType
            """)
    Optional<BookingCapacity> lockCapacity(
            @Param("parkingId") String parkingId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("vehicleType") String vehicleType);
}