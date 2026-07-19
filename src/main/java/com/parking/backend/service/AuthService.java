package com.parking.backend.service;

import com.parking.backend.model.User;
import com.parking.backend.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;

import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    AuthService(
            UserRepository userRepository,
            AuditLogService auditLogService) {

        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public User login(
            String phoneNumber,
            String name,
            String ipAddress) {

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

            auditLogService.log(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    AuditActorRole.USER,
                    AuditAction.USER_LOGIN,
                    "USER",
                    user.getId(),
                    "User logged in successfully",
                    ipAddress,
                    true);

            return user;
        }

        if (name == null || name.isBlank()) {
            throw new RuntimeException("Name is required for new users");
        }

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setRole("USER");
        user.setName(name.trim());

        User savedUser = userRepository.save(user);

        auditLogService.log(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getName(),
                AuditActorRole.USER,
                AuditAction.USER_LOGIN,
                "USER",
                savedUser.getId(),
                "New user registered and logged in",
                ipAddress,
                true);

        return savedUser;
    }

    @Transactional
    public void saveUser(User user) { // Admin Website (Guards m1)
        userRepository.save(user);
    }

}