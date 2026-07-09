package com.parking.backend.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
@Entity
@Table(
    name = "booking_capacity",
    uniqueConstraints = {
        @UniqueConstraint(
            columnNames = {
                "parkingId",
                "bookingDate",
                "vehicleType"
            })
    },
    indexes = {
        @Index(name = "idx_capacity_lookup", columnList = "parkingId,bookingDate,vehicleType")
    }
)
public class BookingCapacity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String parkingId;

    private LocalDate bookingDate;

    private String vehicleType;

    @PositiveOrZero
    private int bookingCapacity;

    @PositiveOrZero
    private int bookedCount;

    @Version
    private Long version;
}