package com.parking.backend.repository;

import com.parking.backend.model.Booking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, String> {

        List<Booking> findByStatus(String status);

        List<Booking> findByUserId(String userId);

        Optional<Booking> findByBookingId(String bookingId);

        List<Booking> findByStatusIn(List<String> statuses);

        List<Booking> findByTypeAndStatus(String type, String status);

        List<Booking> findByStatusAndEndTimeBefore(String status, LocalDateTime time);

        List<Booking> findByEntryTimeIsNullAndStartTimeBefore(LocalDateTime time);

        List<Booking> findByParkingId(String parkingId);

        List<Booking> findByVehicleNumberAndStatus(
                        String vehicleNumber,
                        String status);

        Optional<Booking> findTopByVehicleNumberAndStatusOrderByStartTimeAsc(
                        String vehicleNumber,
                        String status);

        List<Booking> findByEntryTimeIsNullAndStartTimeBeforeAndStatusNot(
                        LocalDateTime time,
                        String status);

        Optional<Booking> findTopByVehicleNumberAndStatusInOrderByStartTimeAsc(
                        String vehicleNumber,
                        List<String> statuses);

        Optional<Booking> findTopByVehicleNumberAndStatus(
                        String vehicleNumber,
                        String status);

        List<Booking> findByParkingIdAndStatusIn(
                        String parkingId,
                        List<String> statuses);

        List<Booking> findByParkingIdAndStatusOrderByStartTimeAsc(
                        String parkingId,
                        String status);

        List<Booking> findByParkingIdAndTypeAndStatus(
                        String parkingId,
                        String type,
                        String status);

        List<Booking> findByParkingIdAndType(
                        String parkingId,
                        String type);

        List<Booking> findByStatusAndEntryTimeIsNullAndStartTimeBefore(
                        String status,
                        LocalDateTime time);

        long countByParkingIdAndVehicleTypeAndStatus(
                        String parkingId,
                        String vehicleType,
                        String status);

        long countByParkingIdAndVehicleTypeAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                        String parkingId,
                        String vehicleType,
                        List<String> statuses,
                        LocalDateTime endTime,
                        LocalDateTime startTime);

        Optional<Booking> findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                        String vehicleNumber,
                        String parkingId,
                        List<String> statuses);

        List<Booking> findByVehicleNumberAndStatusIn(
                        String vehicleNumber,
                        List<String> statuses);

        long countByStatusIn(List<String> statuses);

        long countByParkingIdAndVehicleTypeAndStatusIn(
                        String parkingId,
                        String vehicleType,
                        List<String> statuses);

        long countByParkingIdAndVehicleTypeAndTypeNotAndStatusAndStartTimeBetween(
                        String parkingId,
                        String vehicleType,
                        String type,
                        String status,
                        LocalDateTime start,
                        LocalDateTime end);

        long countByParkingIdAndVehicleTypeAndTypeNotAndStatus(
                        String parkingId,
                        String vehicleType,
                        String type,
                        String status);

        long countByParkingIdAndVehicleTypeAndTypeAndStatus(
                        String parkingId,
                        String vehicleType,
                        String type,
                        String status);
}