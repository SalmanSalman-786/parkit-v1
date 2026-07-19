package com.parking.backend.controller;

import com.parking.backend.model.User;
import com.parking.backend.model.Vehicle;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import java.util.ArrayList;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;

@RestController
//@CrossOrigin("*") // 🔥 ADD THIS LINE
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public UserController(
            UserRepository userRepository,
            BookingRepository bookingRepository) {

        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    @PostMapping
    public User createUser(@Valid @RequestBody User user) {
        return userRepository.save(user);
    }

    @GetMapping("/{id}")
    public User getUser(@PathVariable String id, Authentication auth) {

        String userId = auth.getName(); // 🔥 now userId

        if (!userId.equals(id)) {
            throw new RuntimeException("Unauthorized");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    @PutMapping("/add-vehicle/{userId}")
    public User addVehicle(
            @PathVariable String userId,
            @Valid @RequestBody Vehicle vehicle,
            Authentication auth) {

        String loggedInUserId = auth.getName();

        if (!loggedInUserId.equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getVehicles() == null) {
            user.setVehicles(new ArrayList<>());
        }

        vehicle.setVehicleId(UUID.randomUUID().toString());

        user.getVehicles().add(vehicle);

        return userRepository.save(user);
    }

    @Transactional
    @DeleteMapping("/vehicle/{userId}/{vehicleId}")
    public String deleteVehicle(
            @PathVariable String userId,
            @PathVariable String vehicleId,
            Authentication auth) {

        String loggedInUserId = auth.getName();

        if (!loggedInUserId.equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
        "Unauthorized");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Vehicle vehicle = user.getVehicles()
                .stream()
                .filter(v -> vehicleId.equals(v.getVehicleId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        boolean hasBooking = bookingRepository.existsByVehicleNumberAndStatusIn(
                vehicle.getVehicleNumber(),
                List.of(
                        "PENDING_PAYMENT",
                        "BOOKED",
                        "ACTIVE"));

        if (hasBooking) {
            throw new RuntimeException(
                    "Vehicle cannot be removed because it has active or pending bookings.");
        }

        user.getVehicles().removeIf(v -> vehicleId.equals(v.getVehicleId()));

        userRepository.save(user);

        return "Vehicle removed successfully.";
    }

}