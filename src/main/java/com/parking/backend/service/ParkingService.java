package com.parking.backend.service;

import com.parking.backend.dto.NearbyParkingResponse;
import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;
import com.parking.backend.model.Parking;
import com.parking.backend.model.User;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.ParkingTariffRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.time.LocalDate;

import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ParkingService {

        private final ParkingRepository parkingRepository;

        private final BookingRepository bookingRepository;

        private final UserRepository userRepository;

        private final ParkingTariffRepository parkingTariffRepository;

        private final AuditLogService auditLogService;

        private final CloudinaryService cloudinaryService;

        ParkingService(
                        ParkingRepository parkingRepository,
                        BookingRepository bookingRepository,
                        UserRepository userRepository,
                        ParkingTariffRepository parkingTariffRepository,
                        AuditLogService auditLogService,
                        CloudinaryService cloudinaryService) {

                this.parkingRepository = parkingRepository;
                this.bookingRepository = bookingRepository;
                this.userRepository = userRepository;
                this.parkingTariffRepository = parkingTariffRepository;
                this.auditLogService = auditLogService;
                this.cloudinaryService = cloudinaryService;
        }

        public Parking addParking(
                        Parking parking,
                        String adminId,
                        String ipAddress) {

                if (parking.getName() == null || parking.getName().isEmpty()) {
                        throw new RuntimeException("Parking name required");
                }

                if (parking.getLatitude() == 0 || parking.getLongitude() == 0) {
                        throw new RuntimeException("Invalid location");
                }

                if (parking.getBookingWindowStart() == null) {
                        parking.setBookingWindowStart(LocalDate.now());
                }

                if (parking.getBookingWindowEnd() == null) {
                        parking.setBookingWindowEnd(
                                        parking.getBookingWindowStart().plusDays(30));
                }

                if (parking.getBookingWindowEnd()
                                .isBefore(parking.getBookingWindowStart())) {

                        throw new RuntimeException(
                                        "Booking window end date cannot be before start date.");
                }

                Parking saved = parkingRepository.save(parking);

                User admin = userRepository.findById(adminId)
                                .orElse(null);

                auditLogService.log(
                                adminId,
                                admin != null ? admin.getUsername() : null,
                                admin != null ? admin.getName() : null,
                                AuditActorRole.ADMIN,
                                AuditAction.PARKING_ADDED,
                                "PARKING",
                                saved.getId(),
                                "Parking added: " + saved.getName(),
                                ipAddress,
                                true);

                return saved;
        }

        public List<Parking> getAllParkings(int page, int size) { // User App (Home m1 + details m1 + map m1 + explorer
                                                                  // m1)
                                                                  // + Guard App (parking selection m1 + walkin screen
                                                                  // m1)
                                                                  // + Admin Website (Guards m3 + parkings m1 + Revenue
                                                                  // transactions m2)
                List<Parking> parkings = parkingRepository
                                .findAll(PageRequest.of(page, size))
                                .getContent();

                for (Parking parking : parkings) {

                        int activeCars = (int) bookingRepository
                                        .countByParkingIdAndVehicleTypeAndStatus(
                                                        parking.getId(),
                                                        "FOUR_WHEELER",
                                                        "ACTIVE");

                        int activeBikes = (int) bookingRepository
                                        .countByParkingIdAndVehicleTypeAndStatus(
                                                        parking.getId(),
                                                        "TWO_WHEELER",
                                                        "ACTIVE");

                        parking.setActiveCars(activeCars);
                        parking.setActiveBikes(activeBikes);
                }

                return parkings;
        }

        public NearbyParkingResponse getNearby(double lat, double lng) {

                // First search within 5 km
                double radius = 5 / 111.0;

                double minLat = lat - radius;
                double maxLat = lat + radius;
                double minLng = lng - radius;
                double maxLng = lng + radius;

                List<Parking> parkings = parkingRepository.findNearby(minLat, maxLat, minLng, maxLng);

                if (!parkings.isEmpty()) {
                        return new NearbyParkingResponse(
                                        parkings,
                                        false,
                                        "");
                }

                // Search within 10 km
                radius = 10 / 111.0;

                minLat = lat - radius;
                maxLat = lat + radius;
                minLng = lng - radius;
                maxLng = lng + radius;

                parkings = parkingRepository.findNearby(minLat, maxLat, minLng, maxLng);

                if (!parkings.isEmpty()) {
                        return new NearbyParkingResponse(
                                        parkings,
                                        true,
                                        "No parking found within 5 km. Showing nearby parking within 10 km.");
                }

                // Final fallback
                return new NearbyParkingResponse(
                                parkingRepository.findAll(),
                                true,
                                "No parking found within 10 km. Showing all available parking.");
        }

        public Parking getParkingById(String id) { // Admin Website (Editparking m1 + parking details m1)
                return parkingRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));
        }

        public Parking updateParking(
                        String id,
                        Parking updated,
                        String adminId,
                        String ipAddress) { // Admin Website (Editparking m2)

                Parking parking = parkingRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                parking.setName(updated.getName());
                parking.setLocation(updated.getLocation());

                parking.setLatitude(updated.getLatitude());
                parking.setLongitude(updated.getLongitude());

                String oldImage = parking.getImageUrl();

                if (updated.getImageUrl() != null
                                && !updated.getImageUrl().equals(oldImage)) {

                        parking.setImageUrl(updated.getImageUrl());
                }

                parking.setTwoWheelerCapacity(updated.getTwoWheelerCapacity());
                parking.setTwoWheelerAvailable(updated.getTwoWheelerAvailable());

                parking.setFourWheelerCapacity(updated.getFourWheelerCapacity());
                parking.setFourWheelerAvailable(updated.getFourWheelerAvailable());

                parking.setGraceMinutes(
                                updated.getGraceMinutes());

                parking.setBookingCapacityCars(
                                updated.getBookingCapacityCars());

                parking.setBookingCapacityBikes(
                                updated.getBookingCapacityBikes());

                parking.setBikeHourlyRate(
                                updated.getBikeHourlyRate());

                parking.setCarHourlyRate(
                                updated.getCarHourlyRate());

                parking.setBikeAssuranceDeposit(
                                updated.getBikeAssuranceDeposit());

                parking.setCarAssuranceDeposit(
                                updated.getCarAssuranceDeposit());

                if (updated.getBookingWindowStart() == null ||
                                updated.getBookingWindowEnd() == null) {

                        throw new RuntimeException(
                                        "Booking window start and end dates are required.");
                }

                if (updated.getBookingWindowEnd()
                                .isBefore(updated.getBookingWindowStart())) {

                        throw new RuntimeException(
                                        "Booking window end date cannot be before start date.");
                }

                parking.setBookingWindowStart(
                                updated.getBookingWindowStart());

                parking.setBookingWindowEnd(
                                updated.getBookingWindowEnd());

                Parking saved = parkingRepository.save(parking);

                if (updated.getImageUrl() != null
                                && !updated.getImageUrl().equals(oldImage)) {

                        deleteImage(oldImage);
                }
                User admin = userRepository.findById(adminId)
                                .orElse(null);

                auditLogService.log(
                                adminId,
                                admin != null ? admin.getUsername() : null,
                                admin != null ? admin.getName() : null,
                                AuditActorRole.ADMIN,
                                AuditAction.PARKING_UPDATED,
                                "PARKING",
                                saved.getId(),
                                "Parking updated: " + saved.getName(),
                                ipAddress,
                                true);

                return saved;
        }

        @Transactional
        public void deleteParking(
                        String id,
                        String adminId,
                        String ipAddress) { // Admin Website (parking details m3)

                Parking parking = parkingRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                long activeBookings = bookingRepository.countByParkingIdAndStatusIn(
                                id,
                                List.of(
                                                "PENDING_PAYMENT",
                                                "BOOKED",
                                                "ACTIVE"));

                if (activeBookings > 0) {
                        throw new RuntimeException(
                                        "Cannot delete parking because it has active or upcoming bookings.");
                }

                // Delete all tariffs belonging to this parking
                parkingTariffRepository.deleteByParkingId(id);

                // Delete image
                deleteImage(parking.getImageUrl());

                // Delete parking
                parkingRepository.delete(parking);

                User admin = userRepository.findById(adminId)
                                .orElse(null);

                auditLogService.log(
                                adminId,
                                admin != null ? admin.getUsername() : null,
                                admin != null ? admin.getName() : null,
                                AuditActorRole.ADMIN,
                                AuditAction.PARKING_DELETED,
                                "PARKING",
                                parking.getId(),
                                "Parking deleted: " + parking.getName(),
                                ipAddress,
                                true);
        }

        public String uploadImage(MultipartFile file) {

                try {

                        String contentType = file.getContentType();

                        if (contentType == null ||
                                        (!contentType.equals("image/jpeg")
                                                        && !contentType.equals("image/png")
                                                        && !contentType.equals("image/webp"))) {

                                throw new RuntimeException(
                                                "Only JPG, PNG and WEBP images allowed");
                        }

                        if (file.getSize() > 5 * 1024 * 1024) {

                                throw new RuntimeException(
                                                "Image size must be below 5 MB");
                        }

                        return cloudinaryService.uploadImage(file);

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Image upload failed",
                                        e);
                }
        }

        private void deleteImage(String imageUrl) {

                if (imageUrl == null || imageUrl.isBlank()) {
                        return;
                }

                try {

                        String publicId = imageUrl
                                        .substring(imageUrl.indexOf("/parkit/") + 1)
                                        .replaceFirst("\\.[^.]+$", "");

                        cloudinaryService.deleteImage(publicId);

                } catch (Exception e) {

                        System.err.println("Failed to delete Cloudinary image: " + imageUrl);
                }
        }

}