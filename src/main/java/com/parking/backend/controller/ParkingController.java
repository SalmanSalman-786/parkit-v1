package com.parking.backend.controller;

import com.parking.backend.dto.NearbyParkingResponse;
import com.parking.backend.model.Parking;
import com.parking.backend.service.ParkingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
// @CrossOrigin("*") // 🔥 ADD THIS LINE
@RequestMapping("/api/parking")
public class ParkingController {

    private final ParkingService parkingService;

    ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @PostMapping
    public Parking createParking(
            @Valid @RequestBody Parking parking,
            Authentication auth,
            HttpServletRequest request) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingService.addParking(
                parking,
                auth.getName(),
                getClientIp(request));
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

    @PutMapping("/{id}")
    public Parking updateParking(
            @PathVariable String id,
            @Valid @RequestBody Parking parking,
            Authentication auth,
            HttpServletRequest request) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        return parkingService.updateParking(
                id,
                parking,
                auth.getName(),
                getClientIp(request));
    }

    @DeleteMapping("/{id}")
    public String deleteParking(
            @PathVariable String id,
            Authentication auth,
            HttpServletRequest request) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Unauthorized");
        }

        parkingService.deleteParking(
                id,
                auth.getName(),
                getClientIp(request));

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

    private String getClientIp(HttpServletRequest request) {

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}