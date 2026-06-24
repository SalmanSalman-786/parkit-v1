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

    public User login(String phoneNumber) {

        if (phoneNumber == null || phoneNumber.length() != 10) {
            throw new RuntimeException("Invalid phone number");
        }

        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);

        if (existingUser.isPresent()) {
            return existingUser.get();
        }

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setRole("USER");

        return userRepository.save(user);
    }

    public void saveUser(User user) {   // Admin Website (Guards m1)
        userRepository.save(user);
    }

}