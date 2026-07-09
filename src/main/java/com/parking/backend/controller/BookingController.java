package com.parking.backend.controller;

import com.parking.backend.dto.CancelPreviewResponse;
import com.parking.backend.dto.OperationLookupResponse;
import com.parking.backend.model.Booking;

import com.parking.backend.repository.BookingRepository;

import com.parking.backend.service.BookingService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
//@CrossOrigin("*")
@RequestMapping("/api/booking")
public class BookingController {

    private final BookingService bookingService;

    private final BookingRepository bookingRepository;

    BookingController(BookingService bookingService, BookingRepository bookingRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    // ✅ CREATE BOOKING (USER)

    @PostMapping
    public Booking bookSlot(
            @Valid @RequestBody Booking booking,
            Authentication auth) { // User App (booking screen m7)

        String userId = auth.getName();
        booking.setUserId(userId);

        return bookingService.createBooking(booking);
    }

    // 🚓 ENTRY (GUARD ONLY)
    @PutMapping("/entry/{id}")
    public String markEntry(@PathVariable String id,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return bookingService.markEntry(id);
    }

    // 🚓 EXIT (GUARD ONLY)
    @PutMapping("/exit/{id}")
    public Map<String, Object> markExit(@PathVariable String id,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        Booking booking = bookingService.markExitAndReturn(id);

        Map<String, Object> res = new HashMap<>();
        res.put("message", "Exit successful");
        res.put("amount", booking.getAmount());
        res.put("fine", booking.getFineAmount());

        return res;
    }

    // 🔐 ADMIN ONLY (optional)
    @GetMapping
    public List<Booking> getAllBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return bookingService.getAllBookings(page, size);
    }

    // 🔐 GUARD / ADMIN
    @GetMapping("/active")
    public List<Booking> getActiveBookings(Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) &&
                !auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getActiveBookings();
    }

    @GetMapping("/overtime")
    public List<Booking> getOvertimeBookings(Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) &&
                !auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getOvertimeBookings();
    }

    @GetMapping("/overstayed/{parkingId}") // Guard App (monitoring screen m4 + overstayed screen m1)
    public List<Map<String, Object>> getOverstayedVehicles(
            @PathVariable String parkingId) {

        return bookingService
                .getOverstayedVehicles(parkingId);
    }

    // 👤 USER BOOKINGS (SAFE)
    @GetMapping("/user/{userId}") // User App (booking screen m1 + my booking m1 + profile m1)
    public List<Booking> getUserBookings(@PathVariable String userId,
            Authentication auth) {

        String loggedInUserId = auth.getName();

        if (!loggedInUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getUserBookings(userId);
    }

    // 👤 USER HISTORY (FIXED 🔥)
    @GetMapping("/user/{userId}/history") // User App (my history m1)
    public List<Booking> getUserHistory(@PathVariable String userId,
            Authentication auth) {

        String loggedInUserId = auth.getName();

        if (!loggedInUserId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getUserHistory(userId);
    }

    // 🔐 GET SINGLE BOOKING (SAFE)
    @GetMapping("/{bookingId}")
    public Booking getBooking(@PathVariable String bookingId,
            Authentication auth) {

        Booking booking = bookingService.getBookingByBookingId(bookingId);

        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        // allow only owner or admin
        if (!booking.getUserId().equals(auth.getName()) &&
                !auth.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return booking;
    }

    // 🔐 DETAILS (SAFE)
    @GetMapping("/details/{bookingId}")
    public Map<String, Object> getBookingDetails(@PathVariable String bookingId,
            Authentication auth) {

        Booking booking = bookingService.getBookingByBookingId(bookingId);

        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        // 🔐 ROLE CHECK
        boolean isAdminOrGuard = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_GUARD"));

        // 🔐 USER OWNERSHIP CHECK
        if (!isAdminOrGuard) {
            if (booking.getUserId() == null ||
                    !booking.getUserId().equals(auth.getName())) {

                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
            }
        }

        // ✅ RETURN FROM SERVICE
        return bookingService.getBookingDetails(bookingId);
    }

    @GetMapping("/cancel-preview/{bookingId}")
    public CancelPreviewResponse getCancelPreview(
            @PathVariable String bookingId,
            Authentication authentication) {

        return bookingService.getCancelPreview(
                bookingId,
                authentication.getName());
    }

    // ❌ CANCEL (SAFE)
    @PutMapping("/cancel/{bookingId}") // User App (my booking m2)
    public Map<String, Object> cancelBooking(@PathVariable String bookingId,
            Authentication auth) {

        Booking booking = bookingService.getBookingByBookingId(bookingId);

        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }

        String loggedInUserId = auth.getName();

        if (booking.getUserId() == null ||
                !booking.getUserId().equals(loggedInUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        String msg = bookingService.cancelBooking(bookingId);

        Map<String, Object> res = new HashMap<>();
        res.put("message", msg);

        return res;
    }

    // 🔐 LIVE BOOKINGS (GUARD / ADMIN)
    @GetMapping("/live")
    public List<Booking> getLiveBookings(Authentication auth) {
        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD") ||
                        a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        return bookingService.getLiveBookings();
    }

    @GetMapping("/not-entered/{parkingId}") // Guard App (not entered screen m1)
    public List<Map<String, Object>> getNotEntered(
            @PathVariable String parkingId) {

        return bookingService.getNotEntered(parkingId);
    }

    @PostMapping("/walkin/entry") // Guard App (walkin screen m1)
    public Booking walkinEntry(@RequestBody Map<String, String> req,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.createWalkin(req);
    }

    @GetMapping("/vehicle/{vehicleNumber}")
    public Booking getByVehicle(@PathVariable String vehicleNumber,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD") ||
                        a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getByVehicle(vehicleNumber);
    }

    @GetMapping("/walkin/active")
    public List<Booking> getActiveWalkins(Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_GUARD"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }

        return bookingService.getActiveWalkins(); // always list
    }

    @PostMapping("/confirm-payment") // User App (booking screen m5)
    public Booking confirmPayment(
            @RequestParam String bookingId,
            @RequestParam String paymentId,
            @RequestParam String orderId) {

        return bookingService.confirmPayment(
                bookingId,
                paymentId,
                orderId);
    }

    @PutMapping("/entry/vehicle/{vehicleNumber}/{parkingId}") // Guard App (operations m8)
    public String markEntryByVehicle(
            @PathVariable String vehicleNumber,
            @PathVariable String parkingId) {

        return bookingService.markEntryByVehicle(
                vehicleNumber,
                parkingId);
    }

    @PutMapping("/exit/vehicle/{vehicleNumber}")
    public Booking markExitByVehicle(@PathVariable String vehicleNumber) {
        return bookingService.markExitByVehicle(vehicleNumber);
    }

    @GetMapping("/details/vehicle/{vehicleNumber}/{parkingId}") // Guard App (operations screen m7)
    public Map<String, Object> getBookingByVehicle(
            @PathVariable String vehicleNumber,
            @PathVariable String parkingId,
            Authentication auth) {

        Booking booking = bookingRepository
                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                        vehicleNumber,
                        parkingId,
                        List.of("BOOKED", "ACTIVE"))
                .orElse(null);

        if (booking == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No booking found");
        }

        // 🔥 ROLE CHECK
        boolean isAdminOrGuard = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                        a.getAuthority().equals("ROLE_GUARD"));

        // ❌ Only restrict normal users
        if (!isAdminOrGuard) {
            if (!booking.getUserId().equals(auth.getName())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
            }
        }

        return bookingService.getBookingDetails(booking.getBookingId());
    }

    @GetMapping("/operation-lookup")
    public OperationLookupResponse lookupVehicleOperation(
            @RequestParam String vehicleNumber,
            @RequestParam String parkingId) {

        return bookingService.lookupVehicleOperation(
                vehicleNumber,
                parkingId);
    }

    @GetMapping("/walkin/active/{parkingId}")
    public List<Booking> getActiveWalkins(
            @PathVariable String parkingId,
            Authentication auth) {

        if (!auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority()
                        .equals("ROLE_GUARD"))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Unauthorized");
        }

        return bookingService
                .getActiveWalkins(parkingId);
    }

    @GetMapping("/walkin/stats/{parkingId}")
    public Map<String, Object> getStats(
            @PathVariable String parkingId) {

        return bookingService
                .getWalkinStats(
                        parkingId);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateBooking(
            @Valid @RequestBody Booking booking) {

        bookingService.validateBooking(booking);

        return ResponseEntity.ok().build();
    }

    @PutMapping("/exit/vehicle/{vehicleNumber}/{parkingId}/{mode}") // Guard App (operations screen m2 ,m3 , m5)
    public Booking markExitByVehicle(
            @PathVariable String vehicleNumber,
            @PathVariable String parkingId,
            @PathVariable String mode) {

        return bookingService
                .markExitByVehicle(
                        vehicleNumber,
                        parkingId,
                        mode);
    }

    @GetMapping("/exit-preview/{vehicleNumber}/{parkingId}") // Guard App (operations screen m1)
    public Map<String, Object> exitPreview(
            @PathVariable String vehicleNumber,
            @PathVariable String parkingId) {

        return bookingService.getExitPreview(
                vehicleNumber,
                parkingId);
    }

    @GetMapping("/capacity-breakdown")
    public Map<String, Object> getCapacityBreakdown( // User App m3 (details)
            @RequestParam String parkingId,
            @RequestParam String vehicleType) {

        return bookingService.getCapacityBreakdown(
                parkingId,
                vehicleType);
    }

    @GetMapping("/availability") // User App (booking screen m2)
    public Map<String, Object> getAvailability(
            @RequestParam String parkingId,
            @RequestParam String vehicleType,
            @RequestParam String date) {

        return bookingService.getAvailabilityForDate(
                parkingId,
                vehicleType,
                LocalDate.parse(date));
    }

    @GetMapping("/availability-range")
    public Map<String, Object> getAvailabilityForRange(

            @RequestParam String parkingId,

            @RequestParam String vehicleType,

            @RequestParam String startTime,

            @RequestParam String endTime) {

        return bookingService.getAvailabilityForRange(
                parkingId,
                vehicleType,
                LocalDateTime.parse(startTime),
                LocalDateTime.parse(endTime));
    }

    @GetMapping("/price-preview") // User App (booking screen m3)
    public Map<String, Object> getPricePreview(

            @RequestParam String parkingId,

            @RequestParam String vehicleType,

            @RequestParam String startTime,

            @RequestParam String endTime) {

        return bookingService.getPricePreview(

                parkingId,

                vehicleType,

                LocalDateTime.parse(startTime),

                LocalDateTime.parse(endTime));
    }

    @GetMapping("/long-stay-walkins") // Guard App (longstaywalkin m1 + monitoring screen m7)
    public ResponseEntity<?> getLongStayWalkins(
            @RequestParam String parkingId) {

        return ResponseEntity.ok(
                bookingService.getLongStayWalkins(
                        parkingId));
    }
}