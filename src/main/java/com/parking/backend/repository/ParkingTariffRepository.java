package com.parking.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.parking.backend.model.ParkingTariff;

public interface ParkingTariffRepository extends JpaRepository<ParkingTariff, String> {

        List<ParkingTariff> findByParkingIdOrderByBookingTypeAscVehicleTypeAscSlabTypeAscMaxValueAsc(
                        String parkingId);

        List<ParkingTariff> findByParkingIdAndBookingType(
                        String parkingId,
                        String bookingType);

        List<ParkingTariff> findByParkingIdAndBookingTypeAndVehicleType(
                        String parkingId,
                        String bookingType,
                        String vehicleType);

        @Modifying
        @Transactional
        void deleteByParkingId(String parkingId);

}