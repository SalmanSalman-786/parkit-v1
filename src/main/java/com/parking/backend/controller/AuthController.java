package com.parking.backend.controller;

import com.parking.backend.model.User;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.security.JwtUtil;
import com.parking.backend.service.AuditLogService;
import com.parking.backend.service.AuthService;
import com.parking.backend.dto.UserLoginRequest;
import com.parking.backend.dto.AdminLoginRequest;
import com.parking.backend.dto.GuardLoginRequest;

import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.parking.backend.service.RefreshTokenService;
import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;
import com.parking.backend.model.RefreshToken;
import com.parking.backend.dto.LogoutRequest;
import com.parking.backend.dto.RefreshRequest;

import org.springframework.beans.factory.annotation.Value;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
// @CrossOrigin("*")
@RequestMapping("/api/auth")
public class AuthController {

        private final AuthService authService;

        private final JwtUtil jwtUtil;

        private final PasswordEncoder passwordEncoder;

        private final UserRepository userRepository;

        private final RefreshTokenService refreshTokenService;

        private final AuditLogService auditLogService;

        @Value("${admin.registration.key}")
        private String adminRegistrationKey;

        AuthController(AuthService authService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder,
                        UserRepository userRepository,
                        RefreshTokenService refreshTokenService,
                        AuditLogService auditLogService) {
                this.authService = authService;
                this.jwtUtil = jwtUtil;
                this.passwordEncoder = passwordEncoder;
                this.userRepository = userRepository;

                this.refreshTokenService = refreshTokenService;
                this.auditLogService = auditLogService;
        }

        private static final Logger log = LoggerFactory.getLogger(AuthController.class);

        @PostMapping("/login")
        public Map<String, Object> login(
                        @Valid @RequestBody UserLoginRequest request,
                        HttpServletRequest httpRequest) {

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
                        log.error("Error verifying Firebase ID token", e);
                        throw new RuntimeException(e.getMessage());
                }

                User user = authService.login(
                                request.getPhoneNumber(),
                                request.getName(),
                                getClientIp(httpRequest));

                // if ((user.getName() == null || user.getName().isEmpty())
                // && request.getName() != null && !request.getName().isEmpty()) {

                // user.setName(request.getName());
                // authService.saveUser(user);
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
                        @Valid @RequestBody LogoutRequest request,
                        HttpServletRequest httpRequest) {

                refreshTokenService.findByToken(request.getRefreshToken())
                                .ifPresent(refreshToken -> {

                                        userRepository.findById(refreshToken.getUserId())
                                                        .ifPresent(user -> {

                                                                AuditActorRole role = switch (user.getRole()) {
                                                                        case "ADMIN" -> AuditActorRole.ADMIN;
                                                                        case "GUARD" -> AuditActorRole.GUARD;
                                                                        default -> AuditActorRole.USER;
                                                                };

                                                                auditLogService.log(
                                                                                user.getId(),
                                                                                user.getUsername(),
                                                                                user.getName(),
                                                                                role,
                                                                                AuditAction.LOGOUT,
                                                                                "USER",
                                                                                user.getId(),
                                                                                "User logged out successfully",
                                                                                getClientIp(httpRequest),
                                                                                true);
                                                        });
                                });

                refreshTokenService.deleteToken(request.getRefreshToken());

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
        public String registerGuard(
                        @RequestBody User user,
                        Authentication auth,
                        HttpServletRequest request) {

                User admin = userRepository.findById(auth.getName())
                                .orElseThrow(() -> new RuntimeException("Admin not found"));

                if (user.getPassword() == null || user.getPassword().length() < 6) {
                        throw new RuntimeException("Weak password");
                }

                if (userRepository.findByPhoneNumber(user.getPhoneNumber()).isPresent()) {
                        throw new RuntimeException("Phone number already exists");
                }

                user.setRole("GUARD");
                user.setPassword(passwordEncoder.encode(user.getPassword()));

                if (user.getAssignedParkingId() == null ||
                                user.getAssignedParkingId().isBlank()) {

                        throw new RuntimeException(
                                        "Parking assignment required");
                }

                authService.saveUser(user);

                auditLogService.log(
                                admin.getId(),
                                admin.getUsername(),
                                admin.getName(),
                                AuditActorRole.ADMIN,
                                AuditAction.GUARD_ADDED,
                                "USER",
                                user.getId(),
                                "Guard created: " + user.getUsername()
                                                + " (" + user.getAssignedParkingName() + ")",
                                getClientIp(request),
                                true);

                return "Guard registered";
        }

        @PostMapping("/guard/login")
        public Map<String, Object> guardLogin(
                        @Valid @RequestBody GuardLoginRequest request,
                        HttpServletRequest httpRequest) {

                User user = userRepository.findByUsername(request.getUsername())
                                .orElse(null);

                if (user != null
                                && user.getAccountLockedUntil() != null
                                && user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {

                        auditLogService.log(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getName(),
                                        AuditActorRole.GUARD,
                                        AuditAction.FAILED_LOGIN,
                                        "USER",
                                        user.getId(),
                                        "Guard login failed: Account locked",
                                        getClientIp(httpRequest),
                                        false);

                        throw new RuntimeException(
                                        "Account locked. Try again later.");
                }

                if (user == null) {

                        auditLogService.logAnonymous(
                                        AuditActorRole.GUARD,
                                        AuditAction.FAILED_LOGIN,
                                        request.getUsername(),
                                        "Guard login failed: User not found",
                                        getClientIp(httpRequest));

                        throw new RuntimeException("User not found");
                }

                if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {

                        int attempts = (user.getFailedLoginAttempts() == null
                                        ? 0
                                        : user.getFailedLoginAttempts()) + 1;

                        user.setFailedLoginAttempts(attempts);

                        if (attempts >= 5) {
                                user.setAccountLockedUntil(
                                                LocalDateTime.now().plusMinutes(15));
                        }

                        userRepository.save(user);

                        auditLogService.log(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getName(),
                                        AuditActorRole.GUARD,
                                        AuditAction.FAILED_LOGIN,
                                        "USER",
                                        user.getId(),
                                        "Guard login failed: Invalid password",
                                        getClientIp(httpRequest),
                                        false);

                        throw new RuntimeException("Invalid password");
                }

                // 🔥 CHECK ROLE
                if (!"GUARD".equals(user.getRole())) {
                        throw new RuntimeException("Not a guard account");
                }

                user.setFailedLoginAttempts(0);
                user.setAccountLockedUntil(null);

                userRepository.save(user);

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

                auditLogService.log(
                                user.getId(),
                                user.getUsername(),
                                user.getName(),
                                AuditActorRole.GUARD,
                                AuditAction.GUARD_LOGIN,
                                "USER",
                                user.getId(),
                                "Guard logged in successfully",
                                getClientIp(httpRequest),
                                true);

                return res;
        }

        @PostMapping("/admin/login")
        public Map<String, Object> adminLogin(
                        @Valid @RequestBody AdminLoginRequest request,
                        HttpServletRequest httpRequest) {

                User user = userRepository.findByUsername(request.getUsername())
                                .orElse(null);

                if (user == null) {

                        auditLogService.logAnonymous(
                                        AuditActorRole.ADMIN,
                                        AuditAction.FAILED_LOGIN,
                                        request.getUsername(),
                                        "Admin login failed: User not found",
                                        getClientIp(httpRequest));

                        throw new RuntimeException("User not found");
                }

                if (user.getAccountLockedUntil() != null
                                && user.getAccountLockedUntil().isAfter(LocalDateTime.now())) {

                        auditLogService.log(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getName(),
                                        AuditActorRole.ADMIN,
                                        AuditAction.FAILED_LOGIN,
                                        "USER",
                                        user.getId(),
                                        "Admin login failed: Account locked",
                                        getClientIp(httpRequest),
                                        false);

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

                        auditLogService.log(
                                        user.getId(),
                                        user.getUsername(),
                                        user.getName(),
                                        AuditActorRole.ADMIN,
                                        AuditAction.FAILED_LOGIN,
                                        "USER",
                                        user.getId(),
                                        "Admin login failed: Invalid password",
                                        getClientIp(httpRequest),
                                        false);

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

                auditLogService.log(
                                user.getId(),
                                user.getUsername(),
                                user.getName(),
                                AuditActorRole.ADMIN,
                                AuditAction.ADMIN_LOGIN,
                                "USER",
                                user.getId(),
                                "Admin logged in successfully",
                                getClientIp(httpRequest),
                                true);

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

                log.debug("Refresh endpoint called.");

                RefreshToken refreshToken = refreshTokenService
                                .findByToken(request.getRefreshToken())
                                .orElseThrow(() -> new RuntimeException(
                                                "Invalid refresh token"));

                log.debug("Refresh token found.");

                if (!refreshTokenService.isValid(refreshToken)) {

                        log.warn("Refresh token validation failed.");
                        throw new RuntimeException(
                                        "Refresh token expired");
                }
                log.debug("Refresh token validated successfully.");

                User user = userRepository.findById(
                                refreshToken.getUserId())
                                .orElseThrow(() -> new RuntimeException(
                                                "User not found"));

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

                return response;
        }

        private String getClientIp(HttpServletRequest request) {

                String forwarded = request.getHeader("X-Forwarded-For");

                if (forwarded != null && !forwarded.isBlank()) {
                        return forwarded.split(",")[0].trim();
                }

                return request.getRemoteAddr();
        }

        @GetMapping("/user-exists")
        public Map<String, Object> userExists(@RequestParam String phone) {

                Map<String, Object> response = new HashMap<>();

                User user = userRepository.findByPhoneNumber(phone).orElse(null);

                if (user != null) {
                        response.put("exists", true);
                        response.put("name", user.getName());
                } else {
                        response.put("exists", false);
                }

                return response;
        }
}