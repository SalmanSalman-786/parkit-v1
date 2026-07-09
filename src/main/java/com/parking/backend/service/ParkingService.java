package com.parking.backend.service;

import com.parking.backend.dto.NearbyParkingResponse;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import java.time.LocalDate;

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

        ParkingService(
                        ParkingRepository parkingRepository,
                        BookingRepository bookingRepository) {
                this.parkingRepository = parkingRepository;
                this.bookingRepository = bookingRepository;

        }

        public Parking addParking(Parking parking) {

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

                return parkingRepository.save(parking);
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

        public Parking updateParking(String id, Parking updated) { // Admin Website (Editparking m2)

                Parking parking = parkingRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                parking.setName(updated.getName());
                parking.setLocation(updated.getLocation());

                parking.setLatitude(updated.getLatitude());
                parking.setLongitude(updated.getLongitude());

                parking.setImageUrl(
                                updated.getImageUrl());

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

                return parkingRepository.save(parking);
        }

        public void deleteParking(String id) { // Admin Website (parking details m3)
                parkingRepository.deleteById(id);
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

                        String uploadDir = "uploads/";

                        File directory = new File(uploadDir);

                        if (!directory.exists()) {
                                directory.mkdirs();
                        }

                        String fileName = System.currentTimeMillis()
                                        + "_"
                                        + file.getOriginalFilename()
                                                        .replaceAll("\\s+", "_");

                        Path path = Paths.get(uploadDir + fileName);

                        Files.copy(
                                        file.getInputStream(),
                                        path,
                                        StandardCopyOption.REPLACE_EXISTING);

                        return "/uploads/" + fileName;

                } catch (Exception e) {
                        throw new RuntimeException("Image upload failed");
                }
        }

}