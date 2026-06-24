// package com.parking.backend.repository;

// import java.time.LocalDate;
// import java.util.List;

// import org.springframework.data.mongodb.repository.MongoRepository;

// import com.parking.backend.model.WaitlistRequest;

// public interface WaitlistRepository
//         extends MongoRepository<WaitlistRequest, String> {

//     List<WaitlistRequest> findByParkingIdAndVehicleTypeAndBookingDateOrderByQueuePositionAsc(
//             String parkingId,
//             String vehicleType,
//             LocalDate bookingDate);

//     boolean existsByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
//             String userId,
//             String parkingId,
//             String vehicleType,
//             LocalDate bookingDate);

//     List<WaitlistRequest> findByParkingIdAndVehicleTypeAndBookingDateAndNotifiedFalseOrderByQueuePositionAsc(
//             String parkingId,
//             String vehicleType,
//             LocalDate bookingDate);

//     void deleteByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
//         String userId,
//         String parkingId,
//         String vehicleType,
//         LocalDate bookingDate);

//     List<WaitlistRequest> findByNotifiedTrue();

    
// }

package com.parking.backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.parking.backend.model.WaitlistRequest;

public interface WaitlistRepository
        extends JpaRepository<WaitlistRequest, String> {

    List<WaitlistRequest> findByParkingIdAndVehicleTypeAndBookingDateOrderByQueuePositionAsc(
            String parkingId,
            String vehicleType,
            LocalDate bookingDate);

    boolean existsByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
            String userId,
            String parkingId,
            String vehicleType,
            LocalDate bookingDate);

    List<WaitlistRequest> findByParkingIdAndVehicleTypeAndBookingDateAndNotifiedFalseOrderByQueuePositionAsc(
            String parkingId,
            String vehicleType,
            LocalDate bookingDate);

    void deleteByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
            String userId,
            String parkingId,
            String vehicleType,
            LocalDate bookingDate);

    List<WaitlistRequest> findByNotifiedTrue();
}