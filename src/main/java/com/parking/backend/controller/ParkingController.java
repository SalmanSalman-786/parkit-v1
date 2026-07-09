package com.parking.backend.controller;

import com.parking.backend.dto.NearbyParkingResponse;
import com.parking.backend.model.Parking;
import com.parking.backend.service.ParkingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.List;

@RestController
//@CrossOrigin("*") // 🔥 ADD THIS LINE
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;

    ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @PostMapping
    public Parking createParking(
            @Valid @RequestBody Parking parking,
            Authentication auth) { // Admin Website (Add parking m1)

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingService.addParking(parking);
    }

    @GetMapping
    public List<Parking> getAllParkings( // User App (Home m1 + details m1 + map m1 + explorer m1) + Guard App (parking
                                         // selection m1 + walkin screen m1)
                                         // + Admin Website (Guards m3 + parkings m1 + Revenue transactions m2)
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        size = Math.min(size, 50); // 🔥 LIMIT MAX

        return parkingService.getAllParkings(page, size);
    }

    @GetMapping("/nearby")
    public NearbyParkingResponse getNearby(
            @RequestParam double lat,
            @RequestParam double lng) {

        return parkingService.getNearby(lat, lng);
    }

    @GetMapping("/{id}") // Admin Website (Editparking m1 + parking details m1)
    public Parking getParking(@PathVariable String id) {
        return parkingService.getParkingById(id);
    }

    @PutMapping("/{id}") // Admin Website (Editparking m2)
    public Parking updateParking(
            @PathVariable String id,
            @Valid @RequestBody Parking parking,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingService.updateParking(id, parking);
    }

    @DeleteMapping("/{id}") // Admin Website (parking details m3)
    public String deleteParking(
            @PathVariable String id,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        parkingService.deleteParking(id);

        return "Parking deleted successfully";
    }

    @PostMapping("/upload")
    public String uploadImage(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingService.uploadImage(file);
    }
}