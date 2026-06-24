package com.parking.backend.controller;

import com.parking.backend.model.User;
import com.parking.backend.model.Vehicle;
import com.parking.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import java.util.ArrayList;

import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*") // 🔥 ADD THIS LINE
@RequestMapping("/api/user")
public class UserController {

    private final UserRepository userRepository;

    UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
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

    @PutMapping("/add-vehicle/{userId}")      // User App (add vehicle m1)
    public User addVehicle(@PathVariable String userId,
            @RequestBody Vehicle vehicle,
            org.springframework.security.core.Authentication auth) {

        String loggedInUserId = auth.getName();

        if (!loggedInUserId.equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getVehicles() == null) {
            user.setVehicles(new ArrayList<>());
        }

        user.getVehicles().add(vehicle);

        return userRepository.save(user);
    }
}