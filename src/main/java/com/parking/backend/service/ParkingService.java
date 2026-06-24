package com.parking.backend.service;

import com.parking.backend.model.Parking;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import java.util.List;

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

        public List<Parking> getNearby(double lat, double lng) { // User App m2

                double radius = 5 / 111.0; // approx conversion

                double minLat = lat - radius;
                double maxLat = lat + radius;
                double minLng = lng - radius;
                double maxLng = lng + radius;

                return parkingRepository.findNearby(minLat, maxLat, minLng, maxLng);
        }

        // public boolean decreaseTwoWheelerSlot(String parkingId) {

        // Query query = new Query();
        // query.addCriteria(
        // Criteria.where("_id").is(parkingId)
        // .and("twoWheelerAvailable").gt(0));

        // Update update = new Update().inc("twoWheelerAvailable", -1);

        // UpdateResult result = mongoTemplate.updateFirst(query, update,
        // Parking.class);

        // return result.getModifiedCount() > 0;
        // }

        // public boolean decreaseFourWheelerSlot(String parkingId) {

        // Query query = new Query();
        // query.addCriteria(
        // Criteria.where("_id").is(parkingId)
        // .and("fourWheelerAvailable").gt(0));

        // Update update = new Update().inc("fourWheelerAvailable", -1);

        // UpdateResult result = mongoTemplate.updateFirst(query, update,
        // Parking.class);

        // return result.getModifiedCount() > 0;
        // }

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