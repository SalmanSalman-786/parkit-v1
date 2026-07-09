package com.parking.backend.controller;

import com.parking.backend.model.User;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.security.JwtUtil;
import com.parking.backend.service.AuthService;
import com.parking.backend.dto.UserLoginRequest;
import com.parking.backend.dto.AdminLoginRequest;
import com.parking.backend.dto.GuardLoginRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.parking.backend.service.RefreshTokenService;
import com.parking.backend.model.RefreshToken;
import com.parking.backend.dto.LogoutRequest;
import com.parking.backend.dto.RefreshRequest;

import org.springframework.beans.factory.annotation.Value;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.concurrent.ConcurrentHashMap;

@RestController
//@CrossOrigin("*")
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthService authService;

        private final JwtUtil jwtUtil;

        private final PasswordEncoder passwordEncoder;

        private final UserRepository userRepository;

        private final RefreshTokenService refreshTokenService;

        private final Map<String, Integer> loginAttempts = new ConcurrentHashMap<>();

        @Value("${admin.registration.key}")
        private String adminRegistrationKey;

        AuthController(AuthService authService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                        UserRepository userRepository,
                        RefreshTokenService refreshTokenService) {
                this.authService = authService;
                this.jwtUtil = jwtUtil;
                this.passwordEncoder = passwordEncoder;
                this.userRepository = userRepository;

                this.refreshTokenService = refreshTokenService;
        }

        private void checkLoginAttempts(
                        String username) {

                if (loginAttempts.getOrDefault(
                                username,
                                0) >= 5) {

                        throw new RuntimeException(
                                        "Account temporarily locked");
                }
        }

        @PostMapping("/login")
        public Map<String, Object> login(
                        @Valid @RequestBody UserLoginRequest request) {

                // 🔥 INPUT VALIDATION
                if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
                        throw new RuntimeException("Phone number required");
                }

                if (request.getPhoneNumber().length() != 10) {
                        throw new RuntimeException("Invalid phone number");
                }

                try {

                        FirebaseToken decodedToken = FirebaseAuth.getInstance()
                                        .verifyIdToken(
                                                        request.getFirebaseToken());

                        String firebasePhone = decodedToken.getClaims()
                                        .get("phone_number")
                                        .toString();

                        if (!firebasePhone.endsWith(
                                        request.getPhoneNumber())) {

                                throw new RuntimeException(
                                                "Phone number mismatch");
                        }

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Invalid Firebase token");
                }

                User user = authService.login(
                                request.getPhoneNumber(),
                                request.getName());

                // if ((user.getName() == null || user.getName().isEmpty())
                //                 && request.getName() != null && !request.getName().isEmpty()) {

                //         user.setName(request.getName());
                //         authService.saveUser(user);
                // }

                String accessToken = jwtUtil.generateToken(
                                user.getId(),
                                user.getRole());

                refreshTokenService.revokeAllUserTokens(
                                user.getId());

                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                                user.getId());

                Map<String, Object> response = new HashMap<>();

                response.put(
                                "accessToken",
                                accessToken);

                response.put(
                                "refreshToken",
                                refreshToken.getToken());

                response.put(
                                "userId",
                                user.getId());

                response.put(
                                "role",
                                user.getRole());

                response.put(
                                "name",
                                user.getName());

                return response;
        }

        @PostMapping("/logout")
        public String logout(
                        @Valid @RequestBody LogoutRequest request) {

                System.out.println("🔥 LOGOUT CALLED");
                System.out.println(request.getRefreshToken());

                refreshTokenService.deleteToken(
                                request.getRefreshToken());

                return "Logged out";
        }

        @PostMapping("/admin/register")
        public String registerAdmin(@RequestBody User user,
                        @RequestHeader("admin-key") String key) {

                if (!adminRegistrationKey.equals(key)) {
                        throw new RuntimeException("Unauthorized");
                }

                if (user.getPassword() == null || user.getPassword().length() < 6) {
                        throw new RuntimeException("Weak password");
                }

                user.setRole("ADMIN");
                user.setPassword(passwordEncoder.encode(user.getPassword()));

                authService.saveUser(user);

                return "Admin registered";
        }

        @PostMapping("/guard/register") // Admin Website (Guards m1)
        public String registerGuard(@RequestBody User user,
                        @RequestHeader("admin-key") String key) {

                if (!adminRegistrationKey.equals(key)) {
                        throw new RuntimeException("Unauthorized");
                }

                if (user.getPassword() == null || user.getPassword().length() < 6) {
                        throw new RuntimeException("Weak password");
                }

                user.setRole("GUARD");
                user.setPassword(passwordEncoder.encode(user.getPassword()));

                if (user.getAssignedParkingId() == null ||
                                user.getAssignedParkingId().isBlank()) {

                        throw new RuntimeException(
                                        "Parking assignment required");
                }

                authService.saveUser(user);

                return "Guard registered";
        }

        @PostMapping("/guard/login") // Guard App (login m1)
        public Map<String, Object> guardLogin(
                        @Valid @RequestBody GuardLoginRequest request) {

                checkLoginAttempts(
                                request.getUsername());

                User user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        loginAttempts.merge(
                                        request.getUsername(),
                                        1,
                                        Integer::sum);

                        throw new RuntimeException(
                                        "Invalid password");
                }

                // 🔥 CHECK ROLE
                if (!"GUARD".equals(user.getRole())) {
                        throw new RuntimeException("Not a guard account");
                }

                loginAttempts.remove(
                                request.getUsername());

                String accessToken = jwtUtil.generateToken(
                                user.getId(),
                                user.getRole());

                refreshTokenService.revokeAllUserTokens(
                                user.getId());

                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                                user.getId());

                Map<String, Object> res = new HashMap<>();

                res.put("accessToken", accessToken);

                res.put("refreshToken",
                                refreshToken.getToken());

                res.put("role", user.getRole());

                res.put("assignedParkingId",
                                user.getAssignedParkingId());

                res.put("assignedParkingName",
                                user.getAssignedParkingName());

                res.put("name",
                                user.getName());

                return res;
        }

        @PostMapping("/admin/login") // Admin Website (login m1)
        public Map<String, Object> adminLogin(
                        @Valid @RequestBody AdminLoginRequest request) {

                User user = userRepository.findByUsername(request.getUsername())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (user.getAccountLockedUntil() != null
                                && user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {

                        throw new RuntimeException(
                                        "Account locked. Try again later.");
                }

                if (!passwordEncoder.matches(
                                request.getPassword(),
                                user.getPassword())) {

                        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts())
                                        + 1;

                        user.setFailedLoginAttempts(attempts);

                        if (attempts >= 5) {

                                user.setAccountLockedUntil(
                                                LocalDateTime.now().plusMinutes(15));
                        }

                        userRepository.save(user);

                        throw new RuntimeException(
                                        "Invalid password");
                }

                // 🔥 CHECK ROLE
                if (!"ADMIN".equals(user.getRole())) {
                        throw new RuntimeException("Not an admin account");
                }

                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);

                userRepository.save(user);

                String token = jwtUtil.generateToken(
                                user.getId(),
                                user.getRole());

                refreshTokenService.revokeAllUserTokens(
                                user.getId());

                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                                user.getId());

                Map<String, Object> res = new HashMap<>();
                res.put("accessToken", token);

                res.put(
                                "refreshToken",
                                refreshToken.getToken());

                res.put("role", user.getRole());

                return res;
        }

        @PostMapping("/save-fcm-token") // User App (login m2)
        public String saveFcmToken(
                        @RequestParam String token,
                        HttpServletRequest request) {

                String userId = (String) request.getAttribute("userId");

                if (userId == null) {
                        return "User not authenticated";
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                user.setFcmToken(token);

                userRepository.save(user);

                return "FCM token saved";
        }

        @PostMapping("/refresh")
        public Map<String, Object> refreshToken(
                        @Valid @RequestBody RefreshRequest request) {

                String time = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                System.out.println("======================================");
                System.out.println("🕒 Time      : " + time);
                System.out.println("🔥 REFRESH ENDPOINT HIT");

                RefreshToken refreshToken = refreshTokenService
                                .findByToken(request.getRefreshToken())
                                .orElseThrow(() -> new RuntimeException(
                                                "Invalid refresh token"));

                System.out.println("🔥 REFRESH TOKEN FOUND");

                if (!refreshTokenService.isValid(refreshToken)) {

                        System.out.println("🔥 REFRESH TOKEN INVALID");
                        throw new RuntimeException(
                                        "Refresh token expired");
                }
                System.out.println("🔥 REFRESH TOKEN VALID");
                System.out.println("👤 USER ID: " + refreshToken.getUserId());

                User user = userRepository.findById(
                                refreshToken.getUserId())
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

                System.out.println("📱 USER ROLE: " + user.getRole());
                String accessToken = jwtUtil.generateToken(
                                user.getId(),
                                user.getRole());

                refreshTokenService.revoke(
                                refreshToken);

                RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(
                                user.getId());

                Map<String, Object> response = new HashMap<>();

                response.put(
                                "accessToken",
                                accessToken);

                response.put(
                                "refreshToken",
                                newRefreshToken.getToken());
                System.out.println("======================================");

                return response;
        }
}