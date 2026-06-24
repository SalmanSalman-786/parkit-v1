package com.parking.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.parking.backend.model.Parking;
import com.parking.backend.model.User;
import com.parking.backend.model.WaitlistRequest;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.repository.WaitlistRepository;

import org.springframework.scheduling.annotation.Scheduled;

@Service
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final ParkingRepository parkingRepository;
    private final FirebaseNotificationService firebaseNotificationService;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            UserRepository userRepository,
            FirebaseNotificationService firebaseNotificationService,
            ParkingRepository parkingRepository) {

        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.firebaseNotificationService = firebaseNotificationService;
        this.parkingRepository = parkingRepository;
    }

    public WaitlistRequest joinWaitlist(
            WaitlistRequest request) {

        boolean exists = waitlistRepository
                .existsByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
                        request.getUserId(),
                        request.getParkingId(),
                        request.getVehicleType(),
                        request.getBookingDate());

        if (exists) {
            throw new RuntimeException(
                    "Already in waitlist");
        }

        List<WaitlistRequest> queue = waitlistRepository
                .findByParkingIdAndVehicleTypeAndBookingDateOrderByQueuePositionAsc(
                        request.getParkingId(),
                        request.getVehicleType(),
                        request.getBookingDate());

        request.setQueuePosition(
                queue.size() + 1);

        request.setCreatedAt(
                LocalDateTime.now());

        request.setNotified(false);

        request.setMissedNotifications(0);

        return waitlistRepository.save(request);
    }

    public WaitlistRequest getNextUser(
            String parkingId,
            String vehicleType,
            LocalDate bookingDate) {

        List<WaitlistRequest> queue = waitlistRepository
                .findByParkingIdAndVehicleTypeAndBookingDateAndNotifiedFalseOrderByQueuePositionAsc(
                        parkingId,
                        vehicleType,
                        bookingDate);

        if (queue.isEmpty()) {
            return null;
        }

        return queue.get(0);
    }

    public void notifyNextUser(
            String parkingId,
            String vehicleType,
            LocalDate bookingDate) {

        List<WaitlistRequest> activeReservations = waitlistRepository.findByNotifiedTrue();

        boolean alreadyReserved = activeReservations.stream()
                .anyMatch(w -> parkingId.equals(w.getParkingId()) &&
                        vehicleType.equals(w.getVehicleType()) &&
                        bookingDate.equals(w.getBookingDate()));

        if (alreadyReserved) {
            return;
        }

        WaitlistRequest next = getNextUser(
                parkingId,
                vehicleType,
                bookingDate);

        if (next == null) {
            return;
        }

        User user = userRepository
                .findById(next.getUserId())
                .orElse(null);

        if (user == null ||
                user.getFcmToken() == null ||
                user.getFcmToken().isBlank()) {
            return;
        }

        firebaseNotificationService.sendNotification(
                user.getFcmToken(),
                "🚗 Slot Available",
                "A slot is now available. You have "
                        + parkingRepository
                                .findById(parkingId)
                                .map(Parking::getWaitlistReservationMinutes)
                                .orElse(15)
                        + " minutes to book.");

        next.setNotified(true);
        next.setNotificationTime(LocalDateTime.now());

        waitlistRepository.save(next);
    }

    public void removeFromWaitlist(
            String userId,
            String parkingId,
            String vehicleType,
            LocalDate bookingDate) {

        waitlistRepository
                .deleteByUserIdAndParkingIdAndVehicleTypeAndBookingDate(
                        userId,
                        parkingId,
                        vehicleType,
                        bookingDate);
    }

    @Scheduled(fixedRate = 60000)
    public void processExpiredNotifications() {

        List<WaitlistRequest> notifiedUsers = waitlistRepository.findByNotifiedTrue();

        for (WaitlistRequest request : notifiedUsers) {

            Parking parking = parkingRepository
                    .findById(request.getParkingId())
                    .orElse(null);

            if (parking == null) {
                continue;
            }

            if (request.getNotificationTime() == null) {
                continue;
            }

            int minutes = parking.getWaitlistReservationMinutes();

            if (request.getNotificationTime()
                    .plusMinutes(minutes)
                    .isAfter(LocalDateTime.now())) {

                continue;
            }

            request.setMissedNotifications(
                    request.getMissedNotifications() + 1);

            // Remove after 3 misses
            if (request.getMissedNotifications() >= 3) {

                waitlistRepository.delete(request);

                reorderQueue(
                        request.getParkingId(),
                        request.getVehicleType(),
                        request.getBookingDate());

                continue;
            }

            // Move current user to end of queue
            moveToEnd(request);

            // Notify next user
            notifyNextUser(
                    request.getParkingId(),
                    request.getVehicleType(),
                    request.getBookingDate());
        }
    }

    private void reorderQueue(
            String parkingId,
            String vehicleType,
            LocalDate bookingDate) {

        List<WaitlistRequest> queue = waitlistRepository
                .findByParkingIdAndVehicleTypeAndBookingDateOrderByQueuePositionAsc(
                        parkingId,
                        vehicleType,
                        bookingDate);

        int position = 1;

        for (WaitlistRequest item : queue) {

            item.setQueuePosition(position++);

            waitlistRepository.save(item);
        }
    }

    private void moveToEnd(WaitlistRequest request) {

        List<WaitlistRequest> queue = waitlistRepository
                .findByParkingIdAndVehicleTypeAndBookingDateOrderByQueuePositionAsc(
                        request.getParkingId(),
                        request.getVehicleType(),
                        request.getBookingDate());

        int maxPosition = queue.stream()
                .mapToInt(WaitlistRequest::getQueuePosition)
                .max()
                .orElse(0);

        request.setQueuePosition(maxPosition + 1);

        request.setNotified(false);

        request.setNotificationTime(null);

        waitlistRepository.save(request);

        reorderQueue(
                request.getParkingId(),
                request.getVehicleType(),
                request.getBookingDate());
    }
}