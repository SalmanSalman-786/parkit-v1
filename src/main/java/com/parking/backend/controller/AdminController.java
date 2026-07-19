package com.parking.backend.controller;

import com.parking.backend.dto.AvailabilitySummaryDto;
import com.parking.backend.dto.ParkingRevenueDto;
import com.parking.backend.dto.RevenueSummaryResponse;
import com.parking.backend.dto.RevenueTransactionDto;
import com.parking.backend.model.Booking;
import com.parking.backend.model.User;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.service.AdminService;
import com.parking.backend.service.BookingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.parking.backend.repository.ParkingRepository;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
// @CrossOrigin("*")
public class AdminController {

        private final UserRepository userRepository;

        private final BookingRepository bookingRepository;

        private final ParkingRepository parkingRepository;

        private final BookingService bookingService;

        private final AdminService adminService;

        AdminController(UserRepository userRepository, BookingRepository bookingRepository,
                        ParkingRepository parkingRepository, BookingService bookingService, AdminService adminService) {
                this.userRepository = userRepository;
                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
                this.bookingService = bookingService;
                this.adminService = adminService;
        }

        @GetMapping("/dashboard")
        public Map<String, Object> getDashboard() { // Admin Website (dashboard m1)

                Map<String, Object> result = new HashMap<>();

                long totalUsers = userRepository.countByRole("USER");

                long totalGuards = userRepository.countByRole("GUARD");

                long totalParkings = parkingRepository.count();

                long activeBookings = bookingRepository.countByTypeAndStatus(
                                "BOOKING",
                                "ACTIVE");

                long activeWalkins = bookingRepository.countByTypeAndStatus(
                                "WALKIN",
                                "ACTIVE");

                LocalDate today = LocalDate.now();

                RevenueSummaryResponse revenue = adminService.getRevenueSummary(today);

                result.put("totalUsers", totalUsers);
                result.put("totalGuards", totalGuards);
                result.put("totalParkings", totalParkings);
                result.put("activeBookings", activeBookings);
                result.put("activeWalkins", activeWalkins);
                result.put("todayRevenue", revenue.getNetRevenue());
                return result;
        }

        @GetMapping("/users") // Admin Website (Users m1)
        public List<Map<String, Object>> getUsers() {

                List<User> users = userRepository.findByRole("USER");

                List<Map<String, Object>> result = new ArrayList<>();

                for (User user : users) {

                        Map<String, Object> map = new HashMap<>();

                        map.put("id", user.getId());
                        map.put("name", user.getName());
                        map.put("phoneNumber", user.getPhoneNumber());

                        int vehicleCount = user.getVehicles() == null
                                        ? 0
                                        : user.getVehicles().size();

                        map.put("vehicleCount", vehicleCount);

                        List<Booking> bookings = bookingRepository.findByUserId(user.getId());

                        map.put("bookingCount", bookings.size());

                        double totalSpent = bookings.stream()
                                        .mapToDouble(Booking::getAmount)
                                        .sum();

                        map.put("totalSpent", totalSpent);

                        result.add(map);
                }

                return result;
        }

        @GetMapping("/users/{userId}") // Admin Website (UserDetails m1)
        public Map<String, Object> getUserDetails(
                        @PathVariable String userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<Booking> bookings = bookingRepository.findByUserId(userId);

                double totalSpent = bookings.stream()
                                .mapToDouble(Booking::getAmount)
                                .sum();

                Map<String, Object> result = new HashMap<>();

                result.put("user", user);
                result.put("bookings", bookings);
                result.put("totalSpent", totalSpent);

                return result;
        }

        @GetMapping("/guards") // Admin Website (Guards m2)
        public List<Map<String, Object>> getGuards() {

                List<User> guards = userRepository.findByRole("GUARD");

                List<Map<String, Object>> result = new ArrayList<>();

                for (User guard : guards) {

                        Map<String, Object> map = new HashMap<>();

                        map.put("id", guard.getId());
                        map.put("name", guard.getName());
                        map.put("phoneNumber", guard.getPhoneNumber());
                        map.put("username", guard.getUsername());

                        map.put(
                                        "assignedParkingId",
                                        guard.getAssignedParkingId());

                        map.put(
                                        "assignedParkingName",
                                        guard.getAssignedParkingName());

                        result.add(map);
                }

                return result;
        }

        @PutMapping("/guards/{id}") // Admin Guard (Guards m4)
        public String updateGuard(
                        @PathVariable String id,
                        @Valid @RequestBody User request) {

                User guard = userRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Guard not found"));

                guard.setName(request.getName());
                guard.setPhoneNumber(request.getPhoneNumber());

                guard.setAssignedParkingId(
                                request.getAssignedParkingId());

                guard.setAssignedParkingName(
                                request.getAssignedParkingName());

                userRepository.save(guard);

                return "Guard updated";
        }

        @DeleteMapping("/guards/{id}")
        public String deleteGuard(
                        @PathVariable String id,
                        Authentication auth,
                        HttpServletRequest request) {

                if (!auth.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                        throw new RuntimeException("Unauthorized");
                }

                adminService.deleteGuard(
                                id,
                                auth.getName(),
                                getClientIp(request));

                return "Guard deleted successfully";
        }

        @GetMapping("/walkins") // Admin Website (Walkins m1)
        public List<Map<String, Object>> getWalkins() {

                List<Booking> bookings = bookingRepository.findByType("WALKIN");

                Map<String, Map<String, Object>> grouped = new HashMap<>();

                for (Booking booking : bookings) {

                        if (!"WALKIN".equals(booking.getType())) {
                                continue;
                        }

                        String vehicle = booking.getVehicleNumber();

                        if (!grouped.containsKey(vehicle)) {

                                Map<String, Object> map = new HashMap<>();

                                map.put("vehicleNumber", vehicle);
                                map.put("phoneNumber",
                                                booking.getPhoneNumber());

                                map.put("walkinCount", 0);
                                map.put("totalAmount", 0.0);

                                grouped.put(vehicle, map);
                        }

                        Map<String, Object> current = grouped.get(vehicle);

                        current.put(
                                        "walkinCount",
                                        (Integer) current.get("walkinCount") + 1);

                        current.put(
                                        "totalAmount",
                                        (Double) current.get("totalAmount")
                                                        + booking.getAmount());
                }

                return new ArrayList<>(grouped.values());
        }

        @GetMapping("/walkins/{vehicleNumber}") // Admin Website (WalkinDetails m1)
        public Map<String, Object> getWalkinDetails(
                        @PathVariable String vehicleNumber) {

                List<Booking> walkins = bookingRepository.findByTypeAndVehicleNumber(
                                "WALKIN",
                                vehicleNumber);

                if (walkins.isEmpty()) {
                        throw new RuntimeException("Walk-in not found");
                }

                double totalAmount = walkins.stream()
                                .mapToDouble(Booking::getAmount)
                                .sum();

                Map<String, Object> result = new HashMap<>();

                result.put("vehicleNumber", vehicleNumber);
                result.put("phoneNumber", walkins.get(0).getPhoneNumber());
                result.put("walkinCount", walkins.size());
                result.put("totalAmount", totalAmount);
                result.put("history", walkins);

                return result;
        }

        @GetMapping("/bookings")
        public List<Booking> getAllBookings() {

                return bookingRepository.findAll();
        }

        @GetMapping("/bookings/{bookingId}") // Admin Website (Booking details m1 + Bookings m1)
        public Map<String, Object> getBookingDetails(
                        @PathVariable String bookingId) {

                return bookingService.getBookingDetails(bookingId);
        }

        @GetMapping("/revenue")
        public RevenueSummaryResponse getRevenue( // Admin Website (Revenue m1)
                        @RequestParam(required = false) String date) {

                LocalDate selectedDate = (date == null || date.isBlank())
                                ? LocalDate.now()
                                : LocalDate.parse(date);

                return adminService.getRevenueSummary(selectedDate);
        }

        @GetMapping("/revenue/transactions") // Admin website (Revenue transactions m1)
        public List<RevenueTransactionDto> getRevenueTransactions(

                        @RequestParam(required = false) String date,

                        @RequestParam(defaultValue = "ALL") String filter,

                        @RequestParam(required = false) String parkingId) {

                LocalDate selectedDate = (date == null || date.isBlank())
                                ? LocalDate.now()
                                : LocalDate.parse(date);

                return adminService
                                .getRevenueTransactions(
                                                selectedDate,
                                                filter,
                                                parkingId);
        }

        @GetMapping("/revenue/parking") // Admin Website (dashboard m3 + Revenue m2)
        public List<ParkingRevenueDto> getParkingRevenue(
                        @RequestParam(required = false) String date) {

                LocalDate selectedDate = (date == null || date.isBlank())
                                ? LocalDate.now()
                                : LocalDate.parse(date);

                return adminService.getParkingRevenue(
                                selectedDate);
        }

        @GetMapping("/revenue/last7days")
        public List<Map<String, Object>> getLast7DaysRevenue() { // Admin Website (dashboard m2)

                return adminService.getLast7DaysRevenue();
        }

        @GetMapping("/availability")
        public AvailabilitySummaryDto getAvailability(
                        @RequestParam String parkingId,
                        @RequestParam String startTime,
                        @RequestParam String endTime,
                        @RequestParam(required = false) String vehicleType) {

                return adminService
                                .getAvailabilitySummary(
                                                parkingId,
                                                LocalDateTime.parse(
                                                                startTime),
                                                LocalDateTime.parse(
                                                                endTime),
                                                vehicleType);
        }

        private String getClientIp(HttpServletRequest request) {

                String forwarded = request.getHeader("X-Forwarded-For");

                if (forwarded != null && !forwarded.isBlank()) {
                        return forwarded.split(",")[0].trim();
                }

                return request.getRemoteAddr();
        }

}