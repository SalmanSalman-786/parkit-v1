package com.parking.backend.service;

import com.parking.backend.model.User;
import com.parking.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;

    AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // public User login(String phoneNumber, String name) {

    // Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);

    // if (existingUser.isPresent()) {
    // return existingUser.get();
    // }

    // User user = new User();
    // user.setPhoneNumber(phoneNumber);
    // user.setRole("USER");
    // user.setName(name); // Set before save

    // return userRepository.save(user);
    // }

    public User login(String phoneNumber, String name) {

        if (phoneNumber == null || phoneNumber.length() != 10) {
            throw new RuntimeException("Invalid phone number");
        }

        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);

        if (existingUser.isPresent()) {

            User user = existingUser.get();

            // Update name if it is empty
            if ((user.getName() == null || user.getName().isBlank())
                    && name != null && !name.isBlank()) {

                user.setName(name.trim());
                return userRepository.save(user);
            }

            return user;
        }

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setRole("USER");
        user.setName(name.trim());

        return userRepository.save(user);
    }

    public void saveUser(User user) { // Admin Website (Guards m1)
        userRepository.save(user);
    }

}