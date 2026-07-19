package com.parking.backend.service;

import com.parking.backend.dto.CancelPreviewResponse;
import com.parking.backend.dto.OperationLookupResponse;
import com.parking.backend.dto.PaymentStatus;
import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;
import com.parking.backend.model.Booking;
import com.parking.backend.model.NotificationType;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.model.User;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.ZoneId;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

        private final BookingRepository bookingRepository;

        private final ParkingRepository parkingRepository;

        private final UserRepository userRepository;

        private final RealtimeService realtimeService;

        private final PaymentService paymentService;

        private final WaitlistService waitlistService;

        private final BookingCapacityService bookingCapacityService;

        private final ParkingTariffService parkingTariffService;

        private final NotificationService notificationService;

        private final AuditLogService auditLogService;

        private final Map<String, Object> parkingLocks = new ConcurrentHashMap<>();

        private static final Logger log = LoggerFactory.getLogger(BookingService.class);

        BookingService(
                        BookingRepository bookingRepository,
                        ParkingRepository parkingRepository,
                        UserRepository userRepository,
                        RealtimeService realtimeService,
                        PaymentService paymentService,

                        WaitlistService waitlistService,
                        BookingCapacityService bookingCapacityService,
                        ParkingTariffService parkingTariffService,
                        NotificationService notificationService,
                        AuditLogService auditLogService) {

                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
                this.userRepository = userRepository;
                this.realtimeService = realtimeService; // <-- add this

                this.paymentService = paymentService;
                this.waitlistService = waitlistService;
                this.bookingCapacityService = bookingCapacityService;
                this.parkingTariffService = parkingTariffService;
                this.notificationService = notificationService;
                this.auditLogService = auditLogService;
        }

        public int getBookingCapacity( // new logic
                        Parking parking,
                        String vehicleType) {

                if ("TWO_WHEELER".equals(vehicleType)) {
                        return parking.getBookingCapacityBikes();
                }

                return parking.getBookingCapacityCars();
        }

        public Map<String, Object> getCapacityBreakdown( // User App m3 (details)
                        String parkingId,
                        String vehicleType) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow();

                int totalCapacity = "TWO_WHEELER".equals(vehicleType)
                                ? parking.getTwoWheelerCapacity()
                                : parking.getFourWheelerCapacity();

                int bookingCapacity = getBookingCapacity(
                                parking,
                                vehicleType);
                int walkinCapacity = totalCapacity - bookingCapacity;

                LocalDate today = LocalDate.now();

                LocalDateTime startOfDay = today.atStartOfDay();

                LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

                long bookedToday = bookingRepository
                                .countByParkingIdAndVehicleTypeAndTypeNotAndStatusAndStartTimeBetween(
                                                parkingId,
                                                vehicleType,
                                                "WALKIN",
                                                "BOOKED",
                                                startOfDay,
                                                endOfDay);

                long activeBookings = bookingRepository
                                .countByParkingIdAndVehicleTypeAndTypeNotAndStatus(
                                                parkingId,
                                                vehicleType,
                                                "WALKIN",
                                                "ACTIVE");

                long activeWalkins = bookingRepository
                                .countByParkingIdAndVehicleTypeAndTypeAndStatus(
                                                parkingId,
                                                vehicleType,
                                                "WALKIN",
                                                "ACTIVE");

                long bookingCount = bookedToday + activeBookings;

                int remainingBooking = Math.max(
                                0,
                                bookingCapacity - (int) bookingCount);

                int remainingWalkin = Math.max(
                                0,
                                walkinCapacity - (int) activeWalkins);

                Map<String, Object> result = new HashMap<>();

                result.put("totalCapacity", totalCapacity);

                result.put("bookingCapacity", bookingCapacity);

                result.put("walkinCapacity", walkinCapacity);

                result.put("bookedToday", bookedToday);
                result.put("activeBookings", activeBookings);
                result.put("bookingCount", bookingCount);

                result.put("activeWalkins", activeWalkins);

                result.put("remainingBookingCapacity",
                                remainingBooking);

                result.put("remainingWalkinCapacity",
                                remainingWalkin);

                return result;
        }

        private void reserveBookingCapacity(Booking booking) {

                LocalDate current = booking.getStartTime().toLocalDate();
                LocalDate end = booking.getEndTime().toLocalDate();

                while (!current.isAfter(end)) {

                        bookingCapacityService.reserveSlot(
                                        booking.getParkingId(),
                                        current,
                                        booking.getVehicleType());

                        current = current.plusDays(1);
                }
        }

        private void releaseBookingCapacity(Booking booking) {

                LocalDate current = booking.getStartTime().toLocalDate();
                LocalDate end = booking.getEndTime().toLocalDate();

                while (!current.isAfter(end)) {

                        bookingCapacityService.releaseSlot(
                                        booking.getParkingId(),
                                        current,
                                        booking.getVehicleType());

                        current = current.plusDays(1);
                }
        }

        public Map<String, Object> getAvailabilityForDate( // User App (booking screen m2)
                        String parkingId,
                        String vehicleType,
                        LocalDate bookingDate) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow();

                if (parking.getBookingWindowStart() != null &&
                                bookingDate.isBefore(parking.getBookingWindowStart())) {

                        Map<String, Object> result = new HashMap<>();
                        result.put("date", bookingDate);
                        result.put("bookingCapacity", 0);
                        result.put("bookingCount", 0);
                        result.put("remainingBookingCapacity", 0);
                        result.put("bookingAvailable", false);

                        return result;
                }

                if (parking.getBookingWindowEnd() != null &&
                                bookingDate.isAfter(parking.getBookingWindowEnd())) {

                        Map<String, Object> result = new HashMap<>();
                        result.put("date", bookingDate);
                        result.put("bookingCapacity", 0);
                        result.put("bookingCount", 0);
                        result.put("remainingBookingCapacity", 0);
                        result.put("bookingAvailable", false);

                        return result;
                }

                int bookingCapacity = getBookingCapacity(
                                parking,
                                vehicleType);

                LocalDateTime dayStart = bookingDate.atStartOfDay();

                LocalDateTime nextDay = bookingDate
                                .plusDays(1)
                                .atStartOfDay();

                long bookedForDate = bookingRepository
                                .countBookingsOccupyingDate(
                                                parkingId,
                                                vehicleType,
                                                "WALKIN",
                                                "BOOKED",
                                                dayStart,
                                                nextDay);

                long activeToday = 0;

                if (bookingDate.equals(LocalDate.now())) {

                        activeToday = bookingRepository
                                        .countByParkingIdAndVehicleTypeAndTypeNotAndStatus(
                                                        parkingId,
                                                        vehicleType,
                                                        "WALKIN",
                                                        "ACTIVE");
                }

                long bookingCount = bookedForDate + activeToday;

                int remaining = Math.max(
                                0,
                                bookingCapacity - (int) bookingCount);

                Map<String, Object> result = new HashMap<>();

                result.put("date", bookingDate);

                result.put("bookingCapacity",
                                bookingCapacity);

                result.put("bookingCount",
                                bookingCount);

                result.put("remainingBookingCapacity",
                                remaining);

                return result;
        }

        public Map<String, Object> getAvailabilityForRange(
                        String parkingId,
                        String vehicleType,
                        LocalDateTime startTime,
                        LocalDateTime endTime) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow();

                int bookingCapacity = getBookingCapacity(
                                parking,
                                vehicleType);

                LocalDate current = startTime.toLocalDate();
                LocalDate endDate = endTime.toLocalDate();

                List<Map<String, Object>> days = new ArrayList<>();

                boolean available = true;
                LocalDate blockedDate = null;

                int minimumRemaining = Integer.MAX_VALUE;

                while (!current.isAfter(endDate)) {

                        if (parking.getBookingWindowStart() != null &&
                                        current.isBefore(parking.getBookingWindowStart())) {

                                available = false;
                                blockedDate = current;
                                break;
                        }

                        if (parking.getBookingWindowEnd() != null &&
                                        current.isAfter(parking.getBookingWindowEnd())) {

                                available = false;
                                blockedDate = current;
                                break;
                        }

                        LocalDateTime dayStart = current.atStartOfDay();

                        LocalDateTime nextDay = current
                                        .plusDays(1)
                                        .atStartOfDay();

                        long bookedForDate = bookingRepository.countBookingsOccupyingDate(
                                        parkingId,
                                        vehicleType,
                                        "WALKIN",
                                        "BOOKED",
                                        dayStart,
                                        nextDay);

                        long activeToday = 0;

                        if (current.equals(LocalDate.now())) {

                                activeToday = bookingRepository
                                                .countByParkingIdAndVehicleTypeAndTypeNotAndStatus(
                                                                parkingId,
                                                                vehicleType,
                                                                "WALKIN",
                                                                "ACTIVE");
                        }

                        long bookingCount = bookedForDate + activeToday;

                        int remaining = Math.max(
                                        0,
                                        bookingCapacity - (int) bookingCount);

                        minimumRemaining = Math.min(
                                        minimumRemaining,
                                        remaining);

                        Map<String, Object> day = new HashMap<>();

                        day.put("date", current);

                        day.put("bookingCapacity", bookingCapacity);

                        day.put("bookingCount", bookingCount);

                        day.put("remainingBookingCapacity", remaining);

                        days.add(day);

                        if (remaining <= 0) {

                                available = false;
                                blockedDate = current;
                                break;
                        }

                        current = current.plusDays(1);
                }

                Map<String, Object> result = new HashMap<>();

                result.put("available", available);

                result.put("blockedDate", blockedDate);

                result.put("bookingCapacity", bookingCapacity);

                result.put("remainingBookingCapacity",
                                minimumRemaining == Integer.MAX_VALUE
                                                ? 0
                                                : minimumRemaining);

                result.put("days", days);

                return result;
        }

        public void validateBooking(Booking booking) { // User App (booking screen m6)

                if (booking.getStartTime() == null ||
                                booking.getEndTime() == null) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Start time or End time is missing");
                }

                Parking parking = parkingRepository.findById(booking.getParkingId())
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                LocalDate current = booking.getStartTime().toLocalDate();
                LocalDate endDate = booking.getEndTime().toLocalDate();

                while (!current.isAfter(endDate)) {

                        if (parking.getBookingWindowStart() != null &&
                                        current.isBefore(parking.getBookingWindowStart())) {

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking is not available on " + current);
                        }

                        if (parking.getBookingWindowEnd() != null &&
                                        current.isAfter(parking.getBookingWindowEnd())) {

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking is not available on " + current);
                        }

                        current = current.plusDays(1);
                }

                LocalDateTime start = booking.getStartTime();
                LocalDateTime end = booking.getEndTime();
                // SAME VEHICLE CHECK
                List<Booking> vehicleBookings = bookingRepository.findByVehicleNumberAndStatusIn(
                                booking.getVehicleNumber(),
                                List.of(
                                                "PENDING_PAYMENT",
                                                "BOOKED",
                                                "ACTIVE"));

                for (Booking b : vehicleBookings) {

                        boolean overlap = start.isBefore(b.getEndTime()) &&
                                        end.isAfter(b.getStartTime());

                        if (overlap) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle already has another booking during this time");
                        }
                }

                int capacity = getBookingCapacity(
                                parking,
                                booking.getVehicleType());

                current = booking.getStartTime().toLocalDate();
                endDate = booking.getEndTime().toLocalDate();

                while (!current.isAfter(endDate)) {

                        LocalDateTime dayStart = current.atStartOfDay();

                        LocalDateTime nextDay = current
                                        .plusDays(1)
                                        .atStartOfDay();

                        long bookedForDate = bookingRepository
                                        .countBookingsOccupyingDate(
                                                        booking.getParkingId(),
                                                        booking.getVehicleType(),
                                                        "WALKIN",
                                                        "BOOKED",
                                                        dayStart,
                                                        nextDay);

                        long activeToday = 0;

                        if (current.equals(LocalDate.now())) {

                                activeToday = bookingRepository
                                                .countByParkingIdAndVehicleTypeAndTypeNotAndStatus(
                                                                booking.getParkingId(),
                                                                booking.getVehicleType(),
                                                                "WALKIN",
                                                                "ACTIVE");
                        }

                        long occupied = bookedForDate + activeToday;

                        log.debug(
                                        "Validating booking: parking={}, vehicle={}, start={}, end={}",
                                        booking.getParkingId(),
                                        booking.getVehicleNumber(),
                                        booking.getStartTime(),
                                        booking.getEndTime());

                        if (occupied >= capacity) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking slots full on " + current);
                        }

                        current = current.plusDays(1);
                }
        }

        private double calculateAssuranceDeposit(
                        Parking parking,
                        String vehicleType,
                        LocalDateTime start,
                        LocalDateTime end) {

                long occupiedDays = Duration.between(
                                start.toLocalDate().atStartOfDay(),
                                end.toLocalDate().plusDays(1).atStartOfDay())
                                .toDays();

                double depositPerDay = "TWO_WHEELER".equals(vehicleType)
                                ? parking.getBikeAssuranceDeposit()
                                : parking.getCarAssuranceDeposit();

                return occupiedDays * depositPerDay;
        }

        @Transactional
        public Booking createBooking(
                        Booking booking,
                        String ipAddress) { // User App (booking screen m7)

                Parking parking = parkingRepository.findById(booking.getParkingId())
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                if (booking.getEndTime().isBefore(booking.getStartTime())) {
                        throw new RuntimeException("Invalid time selection");
                }

                User user = userRepository.findById(booking.getUserId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                booking.setPhoneNumber(user.getPhoneNumber());

                booking.setBookingId("BK-" + java.util.UUID.randomUUID());
                booking.setParkingName(parking.getName());
                booking.setLocation(parking.getLocation());
                booking.setParkingImageUrl(parking.getImageUrl());
                booking.setType("BOOKING");
                booking.setStatus("PENDING_PAYMENT");
                booking.setPaymentStatus("PENDING");

                LocalDateTime now = LocalDateTime.now();

                booking.setCreatedAt(now);

                // 🔥 ROUND TIMES
                LocalDateTime start = booking.getStartTime();
                LocalDateTime end = booking.getEndTime();

                if (!end.isAfter(start)) {
                        throw new RuntimeException("Invalid time range");
                }

                if (start.isBefore(LocalDateTime.now())) {
                        throw new RuntimeException(
                                        "Start time must be in the future");
                }

                booking.setStartTime(start);
                booking.setEndTime(end);

                // 🔥 SAME VEHICLE OVERLAP CHECK
                List<Booking> vehicleBookings = bookingRepository.findByVehicleNumberAndStatusIn(
                                booking.getVehicleNumber(),
                                List.of(
                                                "PENDING_PAYMENT",
                                                "BOOKED",
                                                "ACTIVE"));

                for (Booking b : vehicleBookings) {

                        // skip same booking (future update support)
                        if (booking.getBookingId() != null &&
                                        booking.getBookingId().equals(b.getBookingId())) {
                                continue;
                        }

                        boolean overlap = start.isBefore(b.getEndTime()) &&
                                        end.isAfter(b.getStartTime());

                        if (overlap) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle already has another booking during this time");
                        }
                }

                // LocalDate bookingDate = booking.getStartTime().toLocalDate();

                // bookingCapacityService.reserveSlot(
                // booking.getParkingId(),
                // bookingDate,
                // booking.getVehicleType());

                reserveBookingCapacity(booking);

                // 💰 CALCULATE
                long durationMinutes = Duration.between(start, end)
                                .toMinutes();

                double bookingFee = parkingTariffService.calculatePrice(
                                parking.getId(),
                                ParkingTariffService.BOOKING,
                                booking.getVehicleType(),
                                durationMinutes);
                // assurance
                double assuranceDeposit = calculateAssuranceDeposit(
                                parking,
                                booking.getVehicleType(),
                                start,
                                end);

                // save values
                booking.setDurationMinutes(
                                durationMinutes);

                booking.setBookingFee(
                                bookingFee);

                booking.setAssuranceDeposit(
                                assuranceDeposit);

                // total amount to pay now
                booking.setAmount(
                                bookingFee +
                                                assuranceDeposit);

                // 💾 SAVE
                Booking saved = bookingRepository.save(booking);

                auditLogService.log(
                                saved.getUserId(),
                                user != null ? user.getUsername() : null,
                                user != null ? user.getName() : null,
                                AuditActorRole.USER,
                                AuditAction.BOOKING_CREATED,
                                "BOOKING",
                                saved.getBookingId(),
                                "Booking created successfully",
                                ipAddress,
                                true);

                realtimeService.sendDashboardUpdate("BOOKING_CREATED");

                return saved;

        }

        public Map<String, Object> getPricePreview( // User App (booking screen m3)
                        String parkingId,
                        String vehicleType,
                        LocalDateTime start,
                        LocalDateTime end) {

                if (start == null || end == null) {
                        throw new RuntimeException(
                                        "Start time and end time required");
                }

                if (!end.isAfter(start)) {
                        throw new RuntimeException(
                                        "Invalid time range");
                }

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                LocalDate current = start.toLocalDate();
                LocalDate endDate = end.toLocalDate();

                while (!current.isAfter(endDate)) {

                        if (parking.getBookingWindowStart() != null &&
                                        current.isBefore(parking.getBookingWindowStart())) {

                                throw new RuntimeException(
                                                "Booking is not available on " + current);
                        }

                        if (parking.getBookingWindowEnd() != null &&
                                        current.isAfter(parking.getBookingWindowEnd())) {

                                throw new RuntimeException(
                                                "Booking is not available on " + current);
                        }

                        current = current.plusDays(1);
                }

                LocalDateTime now = LocalDateTime.now();

                if (start.isBefore(now)) {
                        throw new RuntimeException(
                                        "Start time must be in future");
                }

                long durationMinutes = Duration.between(start, end)
                                .toMinutes();

                double bookingFee = parkingTariffService.calculatePrice(
                                parking.getId(),
                                ParkingTariffService.BOOKING,
                                vehicleType,
                                durationMinutes);

                double assuranceDeposit = calculateAssuranceDeposit(
                                parking,
                                vehicleType,
                                start,
                                end);

                Map<String, Object> result = new HashMap<>();

                result.put("bookingFee",
                                bookingFee);

                result.put("assuranceDeposit",
                                assuranceDeposit);

                result.put("totalAmount",
                                bookingFee + assuranceDeposit);

                result.put("durationMinutes",
                                durationMinutes);

                return result;
        }

        @Transactional
        public Booking confirmPayment( // User App (booking screen m5)
                        String bookingId,
                        String paymentId,
                        String orderId) {

                log.info(
                                "Confirm payment started for {}",
                                bookingId);

                Booking booking = bookingRepository
                                .findByBookingIdForUpdate(bookingId)
                                .orElseThrow(
                                                () -> new RuntimeException("Booking not found"));

                // ❌ already confirmed
                if ("BOOKED".equals(booking.getStatus())) {

                        log.info(
                                        "Duplicate payment confirmation ignored for booking {}",
                                        booking.getBookingId());

                        return booking;
                }

                if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
                        throw new RuntimeException(
                                        "Invalid booking state");
                }

                booking.setStatus("BOOKED");

                booking.setPaymentStatus("PAID");

                booking.setPaymentMode("ONLINE");

                booking.setRazorpayPaymentId(paymentId);

                booking.setRazorpayOrderId(orderId);

                booking.setPaymentTime(LocalDateTime.now());

                Booking saved = bookingRepository.save(booking);

                User user = userRepository.findById(booking.getUserId())
                                .orElse(null);

                auditLogService.log(
                                booking.getUserId(),
                                user != null ? user.getUsername() : null,
                                user != null ? user.getName() : null,
                                AuditActorRole.USER,
                                AuditAction.PAYMENT_SUCCESS,
                                "BOOKING",
                                booking.getBookingId(),
                                "Online payment successful. Amount: ₹" + booking.getAmount()
                                                + ", Payment ID: " + paymentId,
                                "ONLINE",
                                true);

                log.info(
                                "Booking {} confirmed successfully",
                                booking.getBookingId());

                waitlistService.removeFromWaitlist(
                                booking.getUserId(),
                                booking.getParkingId(),
                                booking.getVehicleType(),
                                booking.getStartTime().toLocalDate());

                notificationService.sendAlert(
                                booking.getUserId(),
                                "🚗 Booking Confirmed",
                                "Your parking slot has been booked successfully.",
                                NotificationType.BOOKING_CONFIRMED);

                realtimeService.sendDashboardUpdate("PAYMENT_CONFIRMED");

                return saved;

        }

        @Scheduled(fixedRate = 60000)
        public void cancelPendingPayments() {

                List<Booking> bookings = bookingRepository.findByStatus("PENDING_PAYMENT");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        if (booking.getCreatedAt() == null) {
                                continue;
                        }

                        if (!booking.getCreatedAt().plusMinutes(3).isBefore(now)) {
                                continue;
                        }

                        if ("PAID".equals(booking.getPaymentStatus())) {
                                continue;
                        }

                        if (booking.getRazorpayOrderId() != null) {

                                try {

                                        PaymentStatus payment = paymentService.checkPayment(
                                                        booking.getRazorpayOrderId());

                                        if (payment.isCaptured()) {

                                                log.info(
                                                                "Recovered payment for booking {}",
                                                                booking.getBookingId());

                                                Booking latest = bookingRepository
                                                                .findByBookingIdForUpdate(
                                                                                booking.getBookingId())
                                                                .orElseThrow();

                                                if (!"BOOKED".equals(latest.getStatus())) {

                                                        confirmPayment(
                                                                        latest.getBookingId(),
                                                                        payment.getPaymentId(),
                                                                        latest.getRazorpayOrderId());
                                                }

                                                continue;

                                        }

                                } catch (Exception e) {

                                        log.error(
                                                        "Payment recovery failed for booking {}",
                                                        booking.getBookingId(),
                                                        e);

                                        // Never cancel after a successful Razorpay payment check.
                                        // Retry again on the next scheduler execution.
                                        continue;
                                }
                        }

                        booking.setStatus("CANCELLED");

                        booking.setCancelled(true);

                        booking.setPaymentStatus("FAILED");

                        releaseBookingCapacity(booking);

                        bookingRepository.save(booking);

                        User user = userRepository.findById(booking.getUserId())
                                        .orElse(null);

                        auditLogService.log(
                                        booking.getUserId(),
                                        user != null ? user.getUsername() : null,
                                        user != null ? user.getName() : null,
                                        AuditActorRole.USER,
                                        AuditAction.BOOKING_EXPIRED,
                                        "BOOKING",
                                        booking.getBookingId(),
                                        "Booking expired due to payment timeout",
                                        "SYSTEM",
                                        true);

                        log.info(
                                        "Booking {} cancelled",
                                        booking.getBookingId());
                }
        }

        // ✅ MARK ENTRY
        @Transactional
        public String markEntry(String bookingId) {

                Booking booking = bookingRepository.findByBookingIdForUpdate(bookingId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking not found"));

                if (!"BOOKED".equals(booking.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid booking status");
                }

                if (booking.getEntryTime() != null) {
                        return "Already entered";
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime start = booking.getStartTime();

                // ❌ TOO EARLY
                if (now.isBefore(start.minusMinutes(30))) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Too early. Entry allowed only 30 minutes before start time");
                }

                // ❌ TOO LATE
                if (now.isAfter(start.plusMinutes(30))) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Booking expired");
                }

                // ✅ VALID ENTRY
                booking.setStatus("ACTIVE");
                booking.setEntryTime(now);
                String refundId = paymentService.refundPayment(
                                booking.getRazorpayPaymentId(),
                                booking.getAssuranceDeposit());

                booking.setDepositRefunded(true);
                booking.setAssuranceDepositRefund(
                                booking.getAssuranceDeposit());
                booking.setDepositRefundTime(now);
                booking.setDepositRefundId(refundId); // if field exists
                booking.setDepositRefundStatus("SUCCESS");
                bookingRepository.save(booking);
                notificationService.sendAlert(
                                booking.getUserId(),
                                "🚗 Vehicle Entered",
                                "Vehicle entry recorded. Your assurance deposit of ₹"
                                                + booking.getAssuranceDeposit()
                                                + " has been refunded.",
                                NotificationType.ENTRY_SUCCESS);
                realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                return "Entry marked";
        }

        @Transactional
        public String markEntryByVehicle(
                        String vehicleNumber,
                        String parkingId,
                        String guardId,
                        String ipAddress) {

                Booking booking = bookingRepository
                                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                vehicleNumber,
                                                parkingId,
                                                List.of("BOOKED"))
                                .orElse(null);
                if (booking == null) {

                        Booking active = bookingRepository
                                        .findTopByVehicleNumberAndStatusOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        "ACTIVE")
                                        .orElse(null);

                        if (active != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle already entered");
                        }

                        Booking completed = bookingRepository
                                        .findTopByVehicleNumberAndStatusOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        "COMPLETED")
                                        .orElse(null);

                        if (completed != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle already exited");
                        }
                        Booking cancelled = bookingRepository
                                        .findTopByVehicleNumberAndStatusOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        "CANCELLED")
                                        .orElse(null);

                        if (cancelled != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking cancelled");
                        }

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Vehicle not found in selected parking");
                }

                Object lock = getLock(booking.getParkingId());

                synchronized (lock) {

                        // 🔥 VALIDATE TIME WINDOW
                        LocalDateTime now = LocalDateTime.now();

                        LocalDateTime start = booking.getStartTime();

                        if (now.isBefore(start.minusMinutes(30))) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Too early for entry");
                        }

                        if (now.isAfter(start.plusMinutes(30))) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking expired");
                        }

                        if (booking.getEntryTime() != null) {
                                return "Already entered";
                        }
                        if (booking.isDepositRefunded()) {
                                return "Deposit already refunded";
                        }

                        booking.setStatus("ACTIVE");
                        booking.setEntryTime(now);

                        String refundId = paymentService.refundPayment(
                                        booking.getRazorpayPaymentId(),
                                        booking.getAssuranceDeposit());

                        booking.setDepositRefunded(true);

                        booking.setAssuranceDepositRefund(
                                        booking.getAssuranceDeposit());
                        booking.setDepositRefundTime(now);
                        booking.setDepositRefundId(refundId); // if field exists
                        booking.setDepositRefundStatus("SUCCESS");

                        bookingRepository.save(booking);

                        User guard = userRepository.findById(guardId)
                                        .orElse(null);

                        auditLogService.log(
                                        guardId,
                                        guard != null ? guard.getUsername() : null,
                                        guard != null ? guard.getName() : null,
                                        AuditActorRole.GUARD,
                                        AuditAction.ENTRY_MARKED,
                                        "BOOKING",
                                        booking.getBookingId(),
                                        "Vehicle entry marked. Vehicle: " + booking.getVehicleNumber(),
                                        ipAddress,
                                        true);
                        notificationService.sendAlert(
                                        booking.getUserId(),
                                        "🚗 Vehicle Entered",
                                        "Vehicle entry recorded. Your assurance deposit of ₹"
                                                        + booking.getAssuranceDeposit()
                                                        + " has been refunded.",
                                        NotificationType.ENTRY_SUCCESS);

                        realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                        return "Entry marked";
                }
        }

        @Scheduled(fixedRate = 60000)
        public void sendBookingReminders() {

                List<Booking> bookings = bookingRepository.findByStatusAndReminderSentFalse(
                                "BOOKED");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        // ❌ no start time
                        if (booking.getStartTime() == null) {
                                continue;
                        }

                        long minutes = Duration.between(
                                        now,
                                        booking.getStartTime()).toMinutes();

                        // 🔥 SEND AT 15 MINUTES
                        if (minutes <= 15 && minutes >= 14) {

                                notificationService.sendAlert(
                                                booking.getUserId(),
                                                "⏰ Parking Reminder",
                                                "Your parking starts in 15 minutes.",
                                                NotificationType.BOOKING_REMINDER);

                                // ✅ prevent duplicate reminder
                                booking.setReminderSent(true);

                                bookingRepository.save(booking);

                                log.info("Booking reminder sent for {}", booking.getBookingId());
                        }
                }
        }

        @Scheduled(fixedRate = 60000)
        public void sendExpiryAlerts() {

                List<Booking> bookings = bookingRepository
                                .findByStatusAndExpiryAlertSentFalse("ACTIVE");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {
                                continue;
                        }

                        if (booking.getEndTime() == null) {
                                continue;
                        }

                        long minutes = Duration.between(
                                        now,
                                        booking.getEndTime()).toMinutes();

                        if (minutes <= 15 && minutes >= 14) {

                                if (booking.getUserId() == null) {
                                        continue;
                                }

                                notificationService.sendAlert(
                                                booking.getUserId(),
                                                "⚠️ Parking Expiry Alert",
                                                "Your parking expires in 15 minutes.",
                                                NotificationType.PARKING_EXPIRY);

                                booking.setExpiryAlertSent(true);

                                bookingRepository.save(booking);

                                log.info("Expiry alert sent for {}", booking.getBookingId());
                        }
                }
        }

        @Scheduled(fixedRate = 60000)
        public void sendStartNotifications() {

                List<Booking> bookings = bookingRepository
                                .findByStatusAndStartNotificationSentFalse("BOOKED");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        if (booking.getStartTime() == null) {
                                continue;
                        }

                        long minutes = Duration.between(
                                        booking.getStartTime(),
                                        now)
                                        .toMinutes();

                        if (minutes >= 0 && minutes <= 1) {

                                notificationService.sendAlert(
                                                booking.getUserId(),
                                                "🚗 Booking Started",
                                                "Your booking has started. Please enter within 30 minutes.",
                                                NotificationType.BOOKING_STARTED);

                                booking.setStartNotificationSent(true);

                                bookingRepository.save(booking);

                                log.info("Start notification sent for {}", booking.getBookingId());
                        }
                }
        }

        private void applyFine(Booking booking) {

                if (!"ACTIVE".equals(booking.getStatus()) || booking.isCancelled()) {
                        return;
                }

                if ("WALKIN".equalsIgnoreCase(booking.getType())) {
                        return;
                }

                if (booking.getEndTime() == null) {
                        return;
                }

                LocalDateTime now = LocalDateTime.now();

                // 🔥 Send warning only once when end time is crossed
                if (now.isAfter(booking.getEndTime()) &&
                                !booking.isEndTimeNotified()) {

                        if (booking.getUserId() != null) {

                                sendMessage(
                                                booking.getUserId(),
                                                "⏰ Your parking time is over. You have a 15-minute grace period.");
                        }

                        notificationService.sendAlert(
                                        booking.getUserId(),
                                        "Parking Time Over",
                                        "You have a 30-minute grace period before fines start.",
                                        NotificationType.PARKING_TIME_OVER);

                        if (booking.getPhoneNumber() != null &&
                                        !booking.getPhoneNumber().isBlank()) {

                        }

                        booking.setEndTimeNotified(true);
                        bookingRepository.save(booking);
                }

                // Grace period

                int graceMinutes = 30;

                LocalDateTime graceEnd = booking.getEndTime()
                                .plusMinutes(graceMinutes);

                if (now.isBefore(graceEnd)) {
                        return;
                }

                // Fine starts after grace period
                long minutesAfterGrace = Duration.between(graceEnd, now)
                                .toMinutes();

                // ₹10 every 10 minutes
                long intervals = minutesAfterGrace / 30;

                double newFine = intervals * 10;

                // Update only when fine increases
                if (newFine > booking.getFineAmount()) {

                        booking.setFineAmount(newFine);

                        if (booking.getUserId() != null) {

                                sendMessage(
                                                booking.getUserId(),
                                                "Late fine updated: ₹" + newFine);
                        }

                        if (booking.getPhoneNumber() != null &&
                                        !booking.getPhoneNumber().isBlank()) {

                        }

                        notificationService.sendAlert(
                                        booking.getUserId(),
                                        "💰 Fine Updated",
                                        "Current fine: ₹" + newFine,
                                        NotificationType.FINE_UPDATED);

                        bookingRepository.save(booking);
                }
        }

        @Transactional
        public String cancelBooking(
                        String bookingId,
                        String ipAddress) {

                Booking booking = bookingRepository.findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                String originalStatus = booking.getStatus();
                boolean originalCancelled = booking.isCancelled();
                double originalRefund = booking.getRefundAmount();
                String originalReason = booking.getCancelReason();

                // ❌ prevent invalid cancel
                if ("COMPLETED".equals(booking.getStatus())) {
                        return "Cannot cancel completed booking";
                }

                if ("ACTIVE".equals(booking.getStatus())) {
                        return "Cannot cancel after entry";
                }

                if ("CANCELLED".equals(booking.getStatus())) {
                        return "Already cancelled";
                }

                CancelPreviewResponse preview = calculateCancellationPreview(booking);

                double bookingRefund = preview.getBookingFeeRefund();

                double depositRefund = preview.getAssuranceDepositRefund();

                double refund = preview.getTotalRefund();
                try {

                        // First cancel booking
                        booking.setStatus("CANCELLED");
                        booking.setCancelled(true);
                        booking.setRefundAmount(refund);
                        booking.setBookingFeeRefund(bookingRefund);
                        booking.setAssuranceDepositRefund(depositRefund);
                        if (refund == 0) {
                                booking.setRefundStatus("NO_REFUND");
                        }
                        booking.setCancelReason("USER");

                        bookingRepository.save(booking);

                        // bookingCapacityService.releaseSlot(
                        // booking.getParkingId(),
                        // booking.getStartTime().toLocalDate(),
                        // booking.getVehicleType());

                        releaseBookingCapacity(booking);

                        // Then try refund
                        if ("PAID".equals(booking.getPaymentStatus())
                                        && booking.getRazorpayPaymentId() != null
                                        && refund > 0) {

                                try {

                                        String refundId = paymentService.refundPayment(
                                                        booking.getRazorpayPaymentId(),
                                                        refund);

                                        booking.setRefundId(refundId);
                                        booking.setRefundStatus("SUCCESS");
                                        booking.setRefundTime(LocalDateTime.now());

                                } catch (Exception e) {

                                        booking.setRefundStatus("FAILED");
                                }

                                bookingRepository.save(booking);

                                User user = userRepository.findById(booking.getUserId())
                                                .orElse(null);

                                auditLogService.log(
                                                booking.getUserId(),
                                                user != null ? user.getUsername() : null,
                                                user != null ? user.getName() : null,
                                                AuditActorRole.USER,
                                                AuditAction.BOOKING_CANCELLED,
                                                "BOOKING",
                                                booking.getBookingId(),
                                                "Booking cancelled by user",
                                                ipAddress, // we'll improve scheduler/controller IP handling later
                                                true);

                        }

                        waitlistService.notifyNextUser(
                                        booking.getParkingId(),
                                        booking.getVehicleType(),
                                        booking.getStartTime().toLocalDate());

                        notificationService.sendAlert(
                                        booking.getUserId(),
                                        "❌ Booking Cancelled",
                                        "Your booking has been cancelled. Refund amount: ₹" + refund,
                                        NotificationType.BOOKING_CANCELLED);

                        // 🔥 STEP 2: RELEASE SLOTS

                        // 🔔 REALTIME
                        realtimeService.sendDashboardUpdate("CANCELLED");

                        return "Cancelled successfully. Refund: ₹" + refund;

                } catch (Exception e) {

                        booking.setStatus(originalStatus);
                        booking.setCancelled(originalCancelled);
                        booking.setRefundAmount(originalRefund);
                        booking.setCancelReason(originalReason);

                        bookingRepository.save(booking);

                        throw new RuntimeException("Cancellation failed. Please try again.");
                }

        }

        private CancelPreviewResponse calculateCancellationPreview(Booking booking) {

                CancelPreviewResponse response = new CancelPreviewResponse();

                response.setBookingFee(booking.getBookingFee());
                response.setAssuranceDeposit(booking.getAssuranceDeposit());

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime start = booking.getStartTime();

                double bookingRefund = 0;
                double depositRefund = 0;

                long occupiedDays = Duration.between(
                                booking.getStartTime().toLocalDate().atStartOfDay(),
                                booking.getEndTime().toLocalDate().plusDays(1).atStartOfDay())
                                .toDays();

                boolean multiDay = occupiedDays > 1;

                long minutesBeforeStart = Duration.between(now, start).toMinutes();

                if (!multiDay) {

                        if (minutesBeforeStart >= 24 * 60) {

                                bookingRefund = booking.getBookingFee();
                                depositRefund = booking.getAssuranceDeposit();

                                response.setMessage("Full refund applicable.");

                        } else if (minutesBeforeStart >= 0) {

                                bookingRefund = booking.getBookingFee();
                                depositRefund = 0;

                                response.setMessage("Booking fee is refundable. Assurance deposit is non-refundable.");

                        } else {

                                response.setMessage("No refund is applicable.");
                        }

                } else {

                        if (minutesBeforeStart >= 24 * 60) {

                                bookingRefund = booking.getBookingFee();
                                depositRefund = 0;

                                response.setMessage("Booking fee is refundable. Assurance deposit is non-refundable.");

                        } else if (minutesBeforeStart >= 0) {

                                bookingRefund = booking.getBookingFee() * 0.5;
                                depositRefund = 0;

                                response.setMessage("50% booking fee refund applicable.");

                        } else {

                                response.setMessage("No refund is applicable.");
                        }
                }

                response.setBookingFeeRefund(bookingRefund);
                response.setAssuranceDepositRefund(depositRefund);
                response.setTotalRefund(bookingRefund + depositRefund);

                return response;
        }

        public CancelPreviewResponse getCancelPreview(
                        String bookingId,
                        String userId) {

                Booking booking = bookingRepository.findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                if (!booking.getUserId().equals(userId)) {
                        throw new RuntimeException("Unauthorized");
                }
                if ("COMPLETED".equals(booking.getStatus())) {
                        throw new RuntimeException("Cannot cancel completed booking");
                }

                if ("ACTIVE".equals(booking.getStatus())) {
                        throw new RuntimeException("Cannot cancel after entry");
                }

                if ("CANCELLED".equals(booking.getStatus())) {
                        throw new RuntimeException("Booking already cancelled");
                }

                return calculateCancellationPreview(booking);
        }

        @Scheduled(fixedRate = 60000)
        public void autoCancelBookings() {

                List<Booking> bookings = bookingRepository.findByStatusIn(List.of("BOOKED", "ACTIVE"));
                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        // 🔥 APPLY FINE (outside lock OK)
                        applyFine(booking);

                        // ❌ ONLY process BOOKED bookings
                        if (!"BOOKED".equals(booking.getStatus())) {
                                continue;
                        }

                        if (booking.getStartTime() == null) {
                                continue;
                        }

                        // ⚠️ WARNING (ONLY ONCE)
                        if (now.isAfter(booking.getStartTime().plusMinutes(15))
                                        && booking.getCancelReason() == null) {

                                sendMessage(
                                                booking.getUserId(),
                                                "⚠️ Your booking will be cancelled soon");

                                notificationService.sendAlert(
                                                booking.getUserId(),
                                                "⚠️ Booking Warning",
                                                "Please enter soon or your booking will be cancelled.",
                                                NotificationType.BOOKING_WARNING);

                                booking.setCancelReason("WARNED");
                                bookingRepository.save(booking);
                        }

                        // AUTO CANCEL
                        if (now.isAfter(booking.getStartTime().plusMinutes(30))
                                        && !"CANCELLED".equals(booking.getStatus())) {

                                // 🔥 STEP 1: UPDATE BOOKING
                                booking.setStatus("CANCELLED");
                                booking.setCancelled(true);
                                booking.setRefundAmount(0);
                                booking.setCancelReason("SYSTEM");
                                booking.setRefundStatus("NO_SHOW");

                                bookingRepository.save(booking);

                                // bookingCapacityService.releaseSlot(
                                // booking.getParkingId(),
                                // booking.getStartTime().toLocalDate(),
                                // booking.getVehicleType());

                                releaseBookingCapacity(booking);

                                waitlistService.notifyNextUser(
                                                booking.getParkingId(),
                                                booking.getVehicleType(),
                                                booking.getStartTime().toLocalDate());

                                // 🔔 REALTIME
                                realtimeService.sendDashboardUpdate("CANCELLED");

                                sendMessage(
                                                booking.getUserId(),
                                                " Booking cancelled. No refund");

                                notificationService.sendAlert(
                                                booking.getUserId(),
                                                "❌ Booking Cancelled",
                                                "Your booking was automatically cancelled because you did not arrive on time.",
                                                NotificationType.AUTO_CANCELLED);
                        }
                }

        }

        public List<Booking> getOvertimeBookings() {
                return bookingRepository
                                .findByStatusAndEndTimeBefore("ACTIVE", LocalDateTime.now());
        }

        public List<Map<String, Object>> getOverstayedVehicles( // Guard App (monitoring screen m4 + overstayed screen
                                                                // m1)
                        String parkingId) {

                LocalDateTime now = LocalDateTime.now();

                int graceMinutes = 30;

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository
                                .findByParkingIdAndStatus(
                                                parkingId,
                                                "ACTIVE");

                for (Booking b : bookings) {

                        if ("WALKIN".equalsIgnoreCase(b.getType()))
                                continue;

                        if (b.getEndTime() == null)
                                continue;

                        if (!b.getEndTime().isBefore(now))
                                continue;
                        long overdueMinutes = Duration
                                        .between(
                                                        b.getEndTime(),
                                                        now)
                                        .toMinutes();

                        String severity;

                        if (overdueMinutes >= graceMinutes * 2) {
                                severity = "RED";
                        } else if (overdueMinutes >= graceMinutes) {
                                severity = "ORANGE";
                        } else {
                                severity = "NORMAL";
                        }

                        String phone = b.getPhoneNumber();

                        if (phone == null && b.getUserId() != null) {

                                User user = userRepository
                                                .findById(b.getUserId())
                                                .orElse(null);

                                if (user != null) {
                                        phone = user.getPhoneNumber();
                                }
                        }

                        Map<String, Object> map = new HashMap<>();

                        map.put("bookingId", b.getBookingId());
                        map.put("vehicleNumber", b.getVehicleNumber());
                        map.put("phoneNumber", phone);
                        map.put("vehicleType", b.getVehicleType());
                        map.put("fineAmount", b.getFineAmount());
                        map.put("type", b.getType());
                        map.put("entryTime", b.getEntryTime());
                        map.put("parkingName", b.getParkingName());
                        map.put("endTime", b.getEndTime());

                        map.put("overdueMinutes",
                                        overdueMinutes);

                        map.put("severity",
                                        severity);

                        map.put("graceMinutes",
                                        graceMinutes);

                        result.add(map);
                }

                return result;
        }

        public List<Map<String, Object>> getNotEntered(String parkingId) { // Guard App (not entered screen m1)

                LocalDateTime now = LocalDateTime.now();

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository.findByParkingIdAndStatus(
                                parkingId,
                                "BOOKED");

                for (Booking b : bookings) {

                        if (b.getEntryTime() != null)
                                continue;

                        if (b.isCancelled())
                                continue;

                        if (b.getStartTime() == null)
                                continue;

                        if (!now.isAfter(b.getStartTime().plusMinutes(5)))
                                continue;

                        String phone = b.getPhoneNumber();

                        if ((phone == null || phone.isBlank())
                                        && b.getUserId() != null) {

                                User user = userRepository
                                                .findById(b.getUserId())
                                                .orElse(null);

                                if (user != null) {
                                        phone = user.getPhoneNumber();
                                }
                        }

                        Map<String, Object> map = new HashMap<>();

                        map.put("bookingId", b.getBookingId());
                        map.put("vehicleNumber", b.getVehicleNumber());
                        map.put("vehicleType", b.getVehicleType());
                        map.put("phoneNumber", phone);
                        map.put("startTime", b.getStartTime());
                        map.put("paymentStatus", b.getPaymentStatus());
                        map.put("type", b.getType());

                        result.add(map);
                }

                return result;
        }

        public Map<String, Object> getExitPreview(String vehicleNumber, String parkingId) { // Guard App (operations
                                                                                            // screen m1)

                Booking booking = bookingRepository
                                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                vehicleNumber,
                                                parkingId,
                                                List.of("ACTIVE"))
                                .orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.BAD_REQUEST,
                                                                "No active booking found"));

                if (!parkingId.equals(
                                booking.getParkingId())) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Vehicle belongs to another parking");
                }

                Parking parking = parkingRepository
                                .findById(booking.getParkingId())
                                .orElseThrow();
                Map<String, Object> res = new HashMap<>();

                double amount = booking.getAmount();

                double fine = booking.getFineAmount();

                // Walk-in amount calculation
                if ("WALKIN".equalsIgnoreCase(
                                booking.getType())) {

                        long minutes = Duration
                                        .between(
                                                        booking.getEntryTime(),
                                                        LocalDateTime.now())
                                        .toMinutes();

                        amount = parkingTariffService.calculatePrice(
                                        parking.getId(),
                                        ParkingTariffService.WALKIN,
                                        booking.getVehicleType(),
                                        minutes);
                }

                res.put("bookingId",
                                booking.getBookingId());

                res.put("vehicleNumber",
                                booking.getVehicleNumber());

                res.put("type",
                                booking.getType());

                res.put("amount",
                                amount);

                res.put("fine",
                                fine);

                double total;

                if ("WALKIN".equalsIgnoreCase(
                                booking.getType())) {

                        total = amount + fine;

                } else {

                        total = fine;
                }

                res.put("total", total);

                return res;
        }

        @Transactional
        public Booking markExitAndReturn(
                        String bookingId,
                        String guardId,
                        String ipAddress) {

                Booking booking = bookingRepository
                                .findByBookingIdForUpdate(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                Parking parking = parkingRepository
                                .findById(booking.getParkingId())
                                .orElseThrow();
                LocalDateTime originalEndTime = booking.getEndTime();

                // ❌ already exited
                if (booking.getExitTime() != null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Already exited");
                }

                // ❌ must be active
                if (!"ACTIVE".equals(booking.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Vehicle not entered");
                }

                LocalDateTime now = LocalDateTime.now()
                                .withSecond(0)
                                .withNano(0);

                booking.setExitTime(now);

                if (!"WALKIN".equalsIgnoreCase(booking.getType())) {
                        booking.setEndTime(now);
                }

                // 🔥 WALK-IN LOGIC
                if ("WALKIN".equalsIgnoreCase(booking.getType())) {

                        long minutes = Duration
                                        .between(booking.getEntryTime(), now)
                                        .toMinutes();

                        booking.setAmount(
                                        parkingTariffService.calculatePrice(
                                                        parking.getId(),
                                                        ParkingTariffService.WALKIN,
                                                        booking.getVehicleType(),
                                                        minutes));
                } else {

                        booking.setAmount(
                                        booking.getBookingFee());

                }

                booking.setStatus("COMPLETED");

                try {

                        Booking saved = bookingRepository.save(booking);

                        User guard = userRepository.findById(guardId)
                                        .orElse(null);

                        auditLogService.log(
                                        guardId,
                                        guard != null ? guard.getUsername() : null,
                                        guard != null ? guard.getName() : null,
                                        AuditActorRole.GUARD,
                                        AuditAction.EXIT_MARKED,
                                        "BOOKING",
                                        saved.getBookingId(),
                                        "Vehicle exited: " + saved.getVehicleNumber(),
                                        ipAddress,
                                        true);

                        if (!"WALKIN".equalsIgnoreCase(saved.getType())) {
                                releaseBookingCapacity(saved);
                        }

                        // ✅ ADD THIS HERE
                        if (saved.getFineAmount() > 0) {

                                notificationService.sendAlert(
                                                saved.getUserId(),
                                                "🚗 Exit Completed",
                                                "Your vehicle has exited successfully.\n"
                                                                + "Parking Fee: ₹" + saved.getAmount()
                                                                + "\nLate Fine: ₹" + saved.getFineAmount()
                                                                + "\nTotal Paid: ₹"
                                                                + (saved.getAmount() + saved.getFineAmount()),
                                                NotificationType.EXIT_SUCCESS);

                        } else {

                                notificationService.sendAlert(
                                                saved.getUserId(),
                                                "🚗 Exit Completed",
                                                "Your vehicle has exited successfully.\n"
                                                                + "Parking Fee: ₹" + saved.getAmount()
                                                                + "\nTotal Paid: ₹" + saved.getAmount(),
                                                NotificationType.EXIT_SUCCESS);
                        }

                        realtimeService.sendDashboardUpdate("EXIT_MARKED");

                        return saved;

                } catch (Exception e) {

                        booking.setAmount(
                                        booking.getBookingFee());

                        booking.setExitTime(null);
                        booking.setEndTime(originalEndTime);
                        booking.setStatus("ACTIVE");

                        bookingRepository.save(booking);

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Exit failed. Please try again.");
                }

        }

        @Transactional
        public Booking markExitByVehicle(
                        String vehicleNumber,
                        String parkingId,
                        String paymentMode,
                        String guardId,
                        String ipAddress) {

                Booking booking = bookingRepository
                                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                vehicleNumber,
                                                parkingId,
                                                List.of("ACTIVE"))
                                .orElse(null);

                if (booking == null) {

                        Booking booked = bookingRepository
                                        .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        parkingId,
                                                        List.of("BOOKED"))
                                        .orElse(null);

                        if (booked != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle has not entered yet");
                        }

                        Booking completed = bookingRepository
                                        .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        parkingId,
                                                        List.of("COMPLETED"))
                                        .orElse(null);

                        if (completed != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle already exited");
                        }

                        Booking cancelled = bookingRepository
                                        .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                        vehicleNumber,
                                                        parkingId,
                                                        List.of("CANCELLED"))
                                        .orElse(null);

                        if (cancelled != null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking was cancelled");
                        }

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Vehicle not found in selected parking");
                }

                if (booking.getFineAmount() > 0) {

                        booking.setFinePaid(true);

                        booking.setFinePaymentMode(paymentMode);

                        booking.setFinePaymentTime(
                                        LocalDateTime.now());

                        booking.setCollectedFineAmount(
                                        booking.getFineAmount());

                }

                booking.setPaymentMode(paymentMode);
                bookingRepository.save(booking);

                return markExitAndReturn(
                                booking.getBookingId(),
                                guardId,
                                ipAddress);

        }

        @Transactional
        public Booking markExitByVehicle(String vehicleNumber) {

                Booking booking = bookingRepository
                                .findTopByVehicleNumberAndStatus(vehicleNumber, "ACTIVE")
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "No active booking found"));

                return markExitAndReturn(
                                booking.getBookingId(),
                                "SYSTEM",
                                "SYSTEM");
        }

        // =====================================
        // WALK-IN METHODS
        // =====================================

        @Transactional
        public Booking createWalkin(
                        Map<String, String> req,
                        String guardId,
                        String ipAddress) { // Guard App (walkin screen m1)

                String parkingId = req.get("parkingId");
                String vehicleType = req.get("vehicleType");
                String vehicleNumber = req.get("vehicleNumber");
                String phone = req.get("phoneNumber");

                if (vehicleNumber != null) {
                        vehicleNumber = vehicleNumber
                                        .trim()
                                        .toUpperCase()
                                        .replace(" ", "")
                                        .replace("-", "");
                }

                if (vehicleNumber == null || vehicleNumber.length() < 4 || vehicleNumber.length() > 15) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid vehicle number format.");
                }

                if (!vehicleNumber.matches("^[A-Z0-9]+$")) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Invalid vehicle number format.");
                }

                Object lock = getLock(parkingId);

                synchronized (lock) {

                        Parking parking = parkingRepository.findById(parkingId)
                                        .orElseThrow(() -> new ResponseStatusException(
                                                        HttpStatus.BAD_REQUEST,
                                                        "parking not found"));

                        // Prevent duplicate active vehicle
                        if (bookingRepository
                                        .findTopByVehicleNumberAndStatus(vehicleNumber, "ACTIVE")
                                        .isPresent()) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Vehicle already inside");
                        }

                        // CURRENT TIME
                        LocalDateTime now = LocalDateTime.now()
                                        .withSecond(0)
                                        .withNano(0);

                        long activeWalkins = bookingRepository
                                        .countByParkingIdAndVehicleTypeAndTypeAndStatus(
                                                        parkingId,
                                                        vehicleType,
                                                        "WALKIN",
                                                        "ACTIVE");
                        int walkinCapacity = Math.max(
                                        0,
                                        "TWO_WHEELER".equals(vehicleType)
                                                        ? parking.getTwoWheelerCapacity()
                                                                        - getBookingCapacity(parking, "TWO_WHEELER")
                                                        : parking.getFourWheelerCapacity()
                                                                        - getBookingCapacity(parking, "FOUR_WHEELER"));
                        if (activeWalkins >= walkinCapacity) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Parking full");
                        }

                        Booking booking = new Booking();

                        booking.setBookingId("WK-" + java.util.UUID.randomUUID());
                        booking.setVehicleNumber(vehicleNumber);
                        booking.setVehicleType(vehicleType);

                        booking.setParkingId(parkingId);
                        booking.setParkingName(parking.getName());
                        booking.setLocation(parking.getLocation());

                        booking.setStartTime(now);
                        booking.setEntryTime(now);
                        booking.setEndTime(null);
                        booking.setStatus("ACTIVE");
                        booking.setType("WALKIN");
                        booking.setPhoneNumber(phone);

                        try {

                                Booking saved = bookingRepository.save(booking);

                                User guard = userRepository.findById(guardId)
                                                .orElse(null);

                                auditLogService.log(
                                                guardId,
                                                guard != null ? guard.getUsername() : null,
                                                guard != null ? guard.getName() : null,
                                                AuditActorRole.GUARD,
                                                AuditAction.WALKIN_ENTRY,
                                                "BOOKING",
                                                saved.getBookingId(),
                                                "Walk-in vehicle entered: " + saved.getVehicleNumber(),
                                                ipAddress,
                                                true);

                                // 🔥 BLOCK CURRENT SLOT ONLY
                                realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                                return saved;

                        } catch (ResponseStatusException e) {
                                throw e;
                        } catch (Exception e) {

                                log.error("Failed to create walk-in booking", e);

                                throw e;
                        }
                }
        }

        public Booking getByVehicle(String vehicleNumber) {

                return bookingRepository
                                .findTopByVehicleNumberAndStatus(vehicleNumber, "ACTIVE")
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Vehicle not found"));
        }

        public List<Booking> getActiveWalkins() {

                List<Booking> list = bookingRepository.findByTypeAndStatus("WALKIN", "ACTIVE");

                return list != null ? list : new ArrayList<>();
        }

        public List<Booking> getActiveWalkins(
                        String parkingId) {

                return bookingRepository
                                .findByParkingIdAndTypeAndStatus(
                                                parkingId,
                                                "WALKIN",
                                                "ACTIVE");
        }

        public Map<String, Object> getWalkinStats(
                        String parkingId) {

                List<Booking> walkins = bookingRepository
                                .findByParkingIdAndType(
                                                parkingId,
                                                "WALKIN");

                int active = 0;
                int todayVehicles = 0;

                double revenue = 0;

                LocalDate today = LocalDate.now();

                for (Booking b : walkins) {

                        if ("ACTIVE".equals(
                                        b.getStatus())) {

                                active++;
                        }

                        if ("COMPLETED".equals(
                                        b.getStatus())) {

                                if (b.getExitTime() != null &&
                                                b.getExitTime()
                                                                .toLocalDate()
                                                                .equals(today)) {

                                        todayVehicles++;

                                        revenue += b.getAmount()
                                                        + b.getFineAmount();
                                }
                        }
                }

                Map<String, Object> res = new HashMap<>();

                res.put("active", active);
                res.put("todayVehicles",
                                todayVehicles);

                res.put("todayRevenue",
                                revenue);

                return res;
        }

        public List<Map<String, Object>> getLongStayWalkins( // Guard App (longstaywalkin m1 + monitoring screen m7)
                        String parkingId) {

                LocalDateTime now = LocalDateTime.now();

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository.findByParkingId(parkingId);

                for (Booking b : bookings) {

                        if (!"ACTIVE".equals(b.getStatus()))
                                continue;

                        if (!"WALKIN".equalsIgnoreCase(b.getType()))
                                continue;

                        if (b.getEntryTime() == null)
                                continue;

                        long hours = Duration
                                        .between(
                                                        b.getEntryTime(),
                                                        now)
                                        .toHours();

                        if (hours < 12)
                                continue;

                        String phone = b.getPhoneNumber();

                        if (phone == null && b.getUserId() != null) {

                                User user = userRepository
                                                .findById(b.getUserId())
                                                .orElse(null);

                                if (user != null) {
                                        phone = user.getPhoneNumber();
                                }
                        }

                        Map<String, Object> map = new HashMap<>();

                        map.put("bookingId", b.getBookingId());
                        map.put("vehicleNumber", b.getVehicleNumber());
                        map.put("vehicleType", b.getVehicleType());
                        map.put("entryTime", b.getEntryTime());
                        map.put("hoursInside", hours);
                        map.put("parkingName", b.getParkingName());
                        map.put("phoneNumber", phone);

                        result.add(map);
                }

                return result;
        }

        // ✅ GET ALL BOOKINGS
        public List<Booking> getAllBookings(int page, int size) {
                return bookingRepository
                                .findAll(org.springframework.data.domain.PageRequest.of(page, size))
                                .getContent();
        }

        // ✅ ACTIVE BOOKINGS
        public List<Booking> getActiveBookings() {
                return bookingRepository.findByStatus("ACTIVE");
        }

        public List<Booking> getLiveBookings() {
                return bookingRepository.findByStatusIn(
                                List.of("BOOKED", "ACTIVE"));
        }

        public List<Booking> getUserBookings(String userId) {
                return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        @Transactional
        public void hideBookingFromUser(
                        String bookingId,
                        String userId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                if (!booking.getUserId().equals(userId)) {
                        throw new RuntimeException("Unauthorized");
                }

                if (!booking.getStatus().equals("COMPLETED")
                                && !booking.getStatus().equals("CANCELLED")) {

                        throw new RuntimeException(
                                        "Only completed or cancelled bookings can be removed.");
                }

                booking.setHiddenFromUser(true);

                bookingRepository.save(booking);
        }

        public List<Booking> getUserHistory(String userId) {
                return bookingRepository
                                .findByUserIdAndHiddenFromUserFalseOrderByCreatedAtDesc(userId)
                                .stream()
                                .filter(b -> "COMPLETED".equals(b.getStatus()) ||
                                                "CANCELLED".equals(b.getStatus()))
                                .toList();
        }

        public Booking getBookingByBookingId(String bookingId) {
                return bookingRepository.findByBookingId(bookingId)
                                .orElse(null);
        }

        public Map<String, Object> getBookingDetails(String bookingId) { // Guard App (operations screen m7) + Admin
                                                                         // Website (Booking details m1 + Bookings m1)

                Booking booking = bookingRepository.findByBookingId(bookingId).orElse(null);

                if (booking == null) {
                        throw new RuntimeException("Booking not found");
                }

                User user = null;

                if (booking.getUserId() != null &&
                                !booking.getUserId().isBlank()) {

                        user = userRepository
                                        .findById(booking.getUserId())
                                        .orElse(null);
                }

                Map<String, Object> result = new HashMap<>();

                result.put("bookingId", booking.getBookingId());
                result.put("vehicleNumber", booking.getVehicleNumber());
                result.put("vehicleType", booking.getVehicleType());
                result.put("parkingName", booking.getParkingName());
                result.put("status", booking.getStatus());

                result.put("amount", booking.getAmount()); // ✅ FIXED
                result.put("fineAmount", booking.getFineAmount());
                result.put("finePaid", booking.isFinePaid());
                result.put("finePaymentMode", booking.getFinePaymentMode());

                result.put("finePaid", booking.isFinePaid());
                result.put("finePaymentMode", booking.getFinePaymentMode());
                result.put("paymentStatus", booking.getPaymentStatus());

                result.put("startTime", booking.getStartTime());
                result.put("entryTime", booking.getEntryTime());
                result.put("exitTime", booking.getExitTime());

                result.put("phoneNumber", booking.getPhoneNumber());

                return result;
        }

        public OperationLookupResponse lookupVehicleOperation(
                        String vehicleNumber,
                        String parkingId) {

                OperationLookupResponse response = new OperationLookupResponse();

                // 1️⃣ ACTIVE → EXIT
                Booking active = bookingRepository
                                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                vehicleNumber,
                                                parkingId,
                                                List.of("ACTIVE"))
                                .orElse(null);

                if (active != null) {

                        response.setAction("EXIT");
                        response.setVehicleNumber(vehicleNumber);
                        response.setBookingId(active.getBookingId());
                        response.setBookingDetails(
                                        getBookingDetails(active.getBookingId()));

                        return response;
                }

                // 2️⃣ BOOKED → ENTRY
                Booking booked = bookingRepository
                                .findTopByVehicleNumberAndParkingIdAndStatusInOrderByStartTimeAsc(
                                                vehicleNumber,
                                                parkingId,
                                                List.of("BOOKED"))
                                .orElse(null);

                if (booked != null) {

                        System.out.println("========== ENTRY DEBUG ==========");
                        System.out.println("Booking ID : " + booked.getBookingId());
                        System.out.println("Vehicle    : " + booked.getVehicleNumber());
                        System.out.println("Start      : " + booked.getStartTime());
                        System.out.println("Now        : " + LocalDateTime.now());
                        System.out.println("Zone       : " + ZoneId.systemDefault());
                        System.out.println("Start-30   : " + booked.getStartTime().minusMinutes(30));
                        System.out.println("Too Early? : " +
                                        LocalDateTime.now().isBefore(booked.getStartTime().minusMinutes(30)));
                        System.out.println("=================================");

                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime start = booked.getStartTime();

                        if (now.isBefore(start.minusMinutes(30))) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Too early for entry");
                        }

                        if (now.isAfter(start.plusMinutes(30))) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Booking expired");
                        }

                        response.setAction("ENTRY");
                        response.setVehicleNumber(vehicleNumber);
                        response.setBookingId(booked.getBookingId());
                        response.setBookingDetails(
                                        getBookingDetails(booked.getBookingId()));

                        return response;
                }

                // 3️⃣ No booking → WALK-IN
                response.setAction("WALKIN");
                response.setVehicleNumber(vehicleNumber);

                return response;
        }
        // ✅ TOTAL REVENUE

        private Object getLock(String parkingId) {
                return parkingLocks.computeIfAbsent(parkingId, k -> new Object());
        }

        public void sendMessage(String userId, String message) {

                String finalMessage;

                try {
                        User user = userRepository.findById(userId).orElse(null);

                        String name = (user != null && user.getName() != null && !user.getName().isEmpty())
                                        ? user.getName()
                                        : "User";

                        finalMessage = "Hi " + name + ", " + message;

                } catch (Exception e) {
                        // fallback if anything fails
                        finalMessage = message;
                }

                // 🔔 YOUR EXISTING SEND LOGIC
                log.info("Message prepared for user {}", userId);

                // If using WebSocket / Notification service → send here
        }

}