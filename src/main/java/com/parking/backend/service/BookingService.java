package com.parking.backend.service;

import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.repository.UserRepository;
import com.parking.backend.model.User;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingService {

        private final BookingRepository bookingRepository;

        private final ParkingRepository parkingRepository;

        private final UserRepository userRepository;

        private final RealtimeService realtimeService;

        private final FirebaseNotificationService firebaseNotificationService;

        private final SmsService smsService;

        private final PaymentService paymentService;

        private final WaitlistService waitlistService;

        private final Map<String, Object> parkingLocks = new ConcurrentHashMap<>();

        BookingService(
                        BookingRepository bookingRepository,
                        ParkingRepository parkingRepository,
                        UserRepository userRepository,
                        RealtimeService realtimeService,
                        FirebaseNotificationService firebaseNotificationService,
                        PaymentService paymentService,
                        SmsService smsService,
                        WaitlistService waitlistService) {

                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
                this.userRepository = userRepository;
                this.realtimeService = realtimeService; // <-- add this
                this.firebaseNotificationService = firebaseNotificationService;
                this.smsService = smsService;
                this.paymentService = paymentService;
                this.waitlistService = waitlistService;
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
                long bookedToday = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .filter(b -> "BOOKED".equals(b.getStatus()))
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> b.getStartTime()
                                                .toLocalDate()
                                                .equals(today))
                                .count();

                long activeBookings = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                .count();

                long activeWalkins = bookingRepository.findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                .filter(b -> "WALKIN".equalsIgnoreCase(b.getType()))
                                .count();

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

        private boolean occupiesDate(
                        Booking booking,
                        LocalDate date) {

                if (booking.getStartTime() == null ||
                                booking.getEndTime() == null) {
                        return false;
                }

                LocalDate startDate = booking.getStartTime().toLocalDate();

                LocalDate endDate = booking.getEndTime().toLocalDate();

                return !date.isBefore(startDate)
                                && !date.isAfter(endDate);
        }

        public Map<String, Object> getAvailabilityForDate( // User App (booking screen m2)
                        String parkingId,
                        String vehicleType,
                        LocalDate bookingDate) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow();

                int bookingCapacity = getBookingCapacity(
                                parking,
                                vehicleType);

                long bookedForDate = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .filter(b -> "BOOKED".equals(b.getStatus()))
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> occupiesDate(b, bookingDate))
                                .count();

                long activeToday = 0;

                if (bookingDate.equals(LocalDate.now())) {

                        activeToday = bookingRepository
                                        .findByParkingId(parkingId)
                                        .stream()
                                        .filter(b -> vehicleType.equals(b.getVehicleType()))
                                        .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                        .filter(b -> "ACTIVE".equals(b.getStatus()))
                                        .count();
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

        public void validateBooking(Booking booking) { // User App (booking screen m6)

                System.out.println("========== VALIDATE BOOKING ==========");
                System.out.println("PARKING ID = " + booking.getParkingId());
                System.out.println("VEHICLE = " + booking.getVehicleNumber());
                System.out.println("TYPE = " + booking.getVehicleType());
                System.out.println("START = " + booking.getStartTime());
                System.out.println("END = " + booking.getEndTime());

                if (booking.getStartTime() == null ||
                                booking.getEndTime() == null) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Start time or End time is missing");
                }

                Parking parking = parkingRepository.findById(booking.getParkingId())
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                LocalDateTime start = booking.getStartTime();
                LocalDateTime end = booking.getEndTime();
                // SAME VEHICLE CHECK
                List<Booking> vehicleBookings = bookingRepository.findByVehicleNumberAndStatusIn(
                                booking.getVehicleNumber(),
                                List.of("BOOKED", "ACTIVE"));

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

                LocalDate bookingDate = booking.getStartTime().toLocalDate();

                long bookedForDate = bookingRepository
                                .findByParkingId(booking.getParkingId())
                                .stream()
                                .filter(b -> booking.getVehicleType()
                                                .equals(b.getVehicleType()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .filter(b -> "BOOKED".equals(b.getStatus()))
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> occupiesDate(b, bookingDate))
                                .count();

                long activeToday = 0;

                if (bookingDate.equals(LocalDate.now())) {

                        activeToday = bookingRepository
                                        .findByParkingId(booking.getParkingId())
                                        .stream()
                                        .filter(b -> booking.getVehicleType()
                                                        .equals(b.getVehicleType()))
                                        .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                        .filter(b -> "ACTIVE".equals(b.getStatus()))
                                        .count();
                }

                long occupied = bookedForDate + activeToday;

                System.out.println("BOOKING DATE = " + bookingDate);
                System.out.println("CAPACITY = " + capacity);
                System.out.println("OCCUPIED = " + occupied);

                if (occupied >= capacity) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Booking slots full");
                }
        }

        public Booking createBooking(Booking booking) { // User App (booking screen m7)

                Object lock = getLock(booking.getParkingId());

                synchronized (lock) {

                        Parking parking = parkingRepository.findById(booking.getParkingId())
                                        .orElseThrow(() -> new RuntimeException("Parking not found"));

                        if (booking.getEndTime().isBefore(booking.getStartTime())) {
                                throw new RuntimeException("Invalid time selection");
                        }

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
                                        List.of("BOOKED", "ACTIVE"));

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

                        int capacity = getBookingCapacity(
                                        parking,
                                        booking.getVehicleType());

                        LocalDate bookingDate = booking.getStartTime().toLocalDate();

                        long bookedForDate = bookingRepository
                                        .findByParkingId(booking.getParkingId())
                                        .stream()
                                        .filter(b -> booking.getVehicleType()
                                                        .equals(b.getVehicleType()))
                                        .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                        .filter(b -> "BOOKED".equals(b.getStatus()))
                                        .filter(b -> b.getStartTime() != null)
                                        .filter(b -> occupiesDate(b, bookingDate))
                                        .count();

                        long activeToday = 0;

                        if (bookingDate.equals(LocalDate.now())) {

                                activeToday = bookingRepository
                                                .findByParkingId(booking.getParkingId())
                                                .stream()
                                                .filter(b -> booking.getVehicleType()
                                                                .equals(b.getVehicleType()))
                                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                                .count();
                        }

                        long occupied = bookedForDate + activeToday;

                        System.out.println("BOOKING DATE = " + bookingDate);
                        System.out.println("CAPACITY = " + capacity);
                        System.out.println("OCCUPIED = " + occupied);

                        if (occupied >= capacity) {
                                throw new RuntimeException(
                                                "Booking slots full");
                        }

                        // 💰 CALCULATE
                        long durationMinutes = Duration.between(start, end)
                                        .toMinutes();

                        double bookingRate = "TWO_WHEELER".equals(
                                        booking.getVehicleType())
                                                        ? parking.getBikeHourlyRate()
                                                        : parking.getCarHourlyRate();

                        double bookingFee = Math.ceil(durationMinutes / 60.0)
                                        * bookingRate;

                        // assurance
                        long reserveMinutes = Duration.between(
                                        now,
                                        start)
                                        .toMinutes();

                        double assuranceDeposit = Math.max(
                                        reserveMinutes,
                                        1);

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
                        parkingRepository.save(parking);

                        realtimeService.sendDashboardUpdate("BOOKING_CREATED");

                        return saved;
                }
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

                LocalDateTime now = LocalDateTime.now();

                if (start.isBefore(now)) {
                        throw new RuntimeException(
                                        "Start time must be in future");
                }

                long durationMinutes = Duration.between(start, end)
                                .toMinutes();

                double bookingRate = "TWO_WHEELER".equals(vehicleType)
                                ? parking.getBikeHourlyRate()
                                : parking.getCarHourlyRate();

                double bookingFee = Math.ceil(durationMinutes / 60.0)
                                * bookingRate;

                long reserveMinutes = Duration.between(now, start)
                                .toMinutes();

                double assuranceDeposit = Math.max(reserveMinutes, 1);

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

        public Booking confirmPayment( // User App (booking screen m5)
                        String bookingId,
                        String paymentId,
                        String orderId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                // ❌ already confirmed
                if ("BOOKED".equals(booking.getStatus())) {
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

                waitlistService.removeFromWaitlist(
                                booking.getUserId(),
                                booking.getParkingId(),
                                booking.getVehicleType(),
                                booking.getStartTime().toLocalDate());
                User user = userRepository
                                .findById(booking.getUserId())
                                .orElse(null);

                if (user != null &&
                                user.getFcmToken() != null &&
                                !user.getFcmToken().isEmpty()) {

                        System.out.println("USER ID = " + user.getId());
                        System.out.println("FCM TOKEN = " + user.getFcmToken());

                        firebaseNotificationService.sendNotification(

                                        user.getFcmToken(),

                                        "🚗 Booking Confirmed",

                                        "Your parking slot has been booked successfully");
                }

                realtimeService.sendDashboardUpdate("PAYMENT_CONFIRMED");

                return saved;
        }

        @Scheduled(fixedRate = 60000)
        public void cancelPendingPayments() {

                List<Booking> bookings = bookingRepository.findByStatus("PENDING_PAYMENT");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        // ⏰ 5 minute timeout
                        if (booking.getCreatedAt() == null) {
                                continue;
                        }

                        if (booking.getCreatedAt().plusMinutes(5).isBefore(now)) {

                                Object lock = getLock(booking.getParkingId());

                                synchronized (lock) {

                                        // ❌ CANCEL BOOKING
                                        booking.setStatus("CANCELLED");

                                        booking.setCancelled(true);

                                        booking.setPaymentStatus("FAILED");

                                        bookingRepository.save(booking);

                                }
                        }
                }
        }

        // ✅ MARK ENTRY
        public String markEntry(String bookingId) {

                Booking booking = bookingRepository.findByBookingId(bookingId)
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
                booking.setDepositRefundTime(now);
                booking.setDepositRefundId(refundId); // if field exists
                booking.setDepositRefundStatus("SUCCESS");
                bookingRepository.save(booking);
                realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                return "Entry marked";
        }

        public String markEntryByVehicle( // Guard App (operations m8)
                        String vehicleNumber,
                        String parkingId) {

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
                booking.setDepositRefundTime(now);
                booking.setDepositRefundId(refundId); // if field exists
                booking.setDepositRefundStatus("SUCCESS");

                bookingRepository.save(booking);

                realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                return "Entry marked";
        }

        @Scheduled(fixedRate = 60000)
        public void sendBookingReminders() {

                List<Booking> bookings = bookingRepository.findByStatus("BOOKED");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        // ❌ already sent
                        if (booking.isReminderSent()) {
                                continue;
                        }

                        // ❌ no start time
                        if (booking.getStartTime() == null) {
                                continue;
                        }

                        long minutes = Duration.between(
                                        now,
                                        booking.getStartTime()).toMinutes();

                        // 🔥 SEND AT 15 MINUTES
                        if (minutes <= 15 && minutes >= 14) {

                                User user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);

                                if (user != null &&
                                                user.getFcmToken() != null &&
                                                !user.getFcmToken().isEmpty()) {

                                        firebaseNotificationService.sendNotification(

                                                        user.getFcmToken(),

                                                        "⏰ Parking Reminder",

                                                        "Your parking starts in 15 minutes");
                                }

                                // ✅ prevent duplicate reminder
                                booking.setReminderSent(true);

                                bookingRepository.save(booking);

                                System.out.println(
                                                "Reminder sent for " +
                                                                booking.getBookingId());
                        }
                }
        }

        @Scheduled(fixedRate = 60000)
        public void sendExpiryAlerts() {

                List<Booking> bookings = bookingRepository.findByStatus("ACTIVE");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {
                                continue;
                        }

                        if (booking.isExpiryAlertSent()) {
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

                                User user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);

                                if (user != null &&
                                                user.getFcmToken() != null &&
                                                !user.getFcmToken().isEmpty()) {

                                        firebaseNotificationService.sendNotification(

                                                        user.getFcmToken(),

                                                        "⚠️ Parking Expiry Alert",

                                                        "Your parking expires in 15 minutes");
                                }

                                booking.setExpiryAlertSent(true);

                                bookingRepository.save(booking);

                                System.out.println(
                                                "Expiry alert sent for " +
                                                                booking.getBookingId());
                        }
                }
        }

        @Scheduled(fixedRate = 60000)
        public void sendStartNotifications() {

                List<Booking> bookings = bookingRepository.findByStatus("BOOKED");

                LocalDateTime now = LocalDateTime.now();

                for (Booking booking : bookings) {

                        if (booking.isStartNotificationSent()) {
                                continue;
                        }

                        if (booking.getStartTime() == null) {
                                continue;
                        }

                        long minutes = Duration.between(
                                        booking.getStartTime(),
                                        now)
                                        .toMinutes();

                        if (minutes >= 0 && minutes <= 1) {

                                User user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);

                                if (user != null &&
                                                user.getFcmToken() != null &&
                                                !user.getFcmToken().isEmpty()) {

                                        firebaseNotificationService.sendNotification(

                                                        user.getFcmToken(),

                                                        "🚗 Booking Started",

                                                        "Your booking has started. Please enter within 30 minutes.");
                                }

                                booking.setStartNotificationSent(true);

                                bookingRepository.save(booking);

                                System.out.println(
                                                "Start notification sent for "
                                                                + booking.getBookingId());
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

                        User user = null;

                        if (booking.getUserId() != null) {
                                user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);
                        }

                        if (user != null &&
                                        user.getFcmToken() != null &&
                                        !user.getFcmToken().isEmpty()) {

                                firebaseNotificationService.sendNotification(
                                                user.getFcmToken(),
                                                "⏰ Parking Time Over",
                                                "You have a 15-minute grace period before fines start.");
                        }

                        if (booking.getPhoneNumber() != null &&
                                        !booking.getPhoneNumber().isBlank()) {

                                smsService.sendSms(
                                                booking.getPhoneNumber(),
                                                "ParkIt: Your parking session has expired. You have a 15-minute grace period before fines begin.");
                        }

                        booking.setEndTimeNotified(true);
                        bookingRepository.save(booking);
                }

                // Grace period

                int graceMinutes = 15;

                LocalDateTime graceEnd = booking.getEndTime()
                                .plusMinutes(graceMinutes);

                if (now.isBefore(graceEnd)) {
                        return;
                }

                // Fine starts after grace period
                long minutesAfterGrace = Duration.between(graceEnd, now)
                                .toMinutes();

                // ₹10 every 10 minutes
                long intervals = minutesAfterGrace / 10;

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

                                smsService.sendSms(
                                                booking.getPhoneNumber(),
                                                "ParkIt: Overtime detected for vehicle "
                                                                + booking.getVehicleNumber()
                                                                + ". Current fine: ₹" + newFine);
                        }

                        User user = null;

                        if (booking.getUserId() != null) {
                                user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);
                        }

                        if (user != null &&
                                        user.getFcmToken() != null &&
                                        !user.getFcmToken().isEmpty()) {

                                try {
                                        firebaseNotificationService.sendNotification(
                                                        user.getFcmToken(),
                                                        "Fine Updated",
                                                        "Current fine: ₹" + newFine);
                                } catch (Exception e) {
                                        System.out.println("FCM failed: " + e.getMessage());
                                }
                        }

                        bookingRepository.save(booking);
                }
        }

        public String cancelBooking(String bookingId) {

                Booking booking = bookingRepository.findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                String originalStatus = booking.getStatus();
                boolean originalCancelled = booking.isCancelled();
                double originalRefund = booking.getRefundAmount();
                String originalReason = booking.getCancelReason();

                Object lock = getLock(booking.getParkingId());

                synchronized (lock) {

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

                        LocalDateTime now = LocalDateTime.now();
                        LocalDateTime start = booking.getStartTime();

                        // ❌ cannot cancel after booking start time
                        if (!now.isBefore(start)) {
                                return "Cannot cancel after booking start time";
                        }

                        // Full refund before start time
                        double refund = booking.getBookingFee()
                                        + booking.getAssuranceDeposit();

                        try {

                                // First cancel booking
                                booking.setStatus("CANCELLED");
                                booking.setCancelled(true);
                                booking.setRefundAmount(refund);
                                booking.setCancelReason("USER");

                                bookingRepository.save(booking);

                                // Then try refund
                                if ("PAID".equals(booking.getPaymentStatus())
                                                && booking.getRazorpayPaymentId() != null) {

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

                                }

                                waitlistService.notifyNextUser(
                                                booking.getParkingId(),
                                                booking.getVehicleType(),
                                                booking.getStartTime().toLocalDate());

                                User user = userRepository
                                                .findById(booking.getUserId())
                                                .orElse(null);

                                if (user != null &&
                                                user.getFcmToken() != null &&
                                                !user.getFcmToken().isEmpty()) {

                                        firebaseNotificationService.sendNotification(
                                                        user.getFcmToken(),
                                                        "❌ Booking Cancelled",
                                                        "Your booking has been cancelled. Refund amount: ₹" + refund);
                                }

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

                        Object lock = getLock(booking.getParkingId());

                        synchronized (lock) {

                                // ⚠️ WARNING (ONLY ONCE)
                                if (now.isAfter(booking.getStartTime().plusMinutes(15))
                                                && booking.getCancelReason() == null) {

                                        sendMessage(
                                                        booking.getUserId(),
                                                        "⚠️ Your booking will be cancelled soon");

                                        User user = userRepository
                                                        .findById(booking.getUserId())
                                                        .orElse(null);

                                        if (user != null &&
                                                        user.getFcmToken() != null &&
                                                        !user.getFcmToken().isEmpty()) {

                                                firebaseNotificationService.sendNotification(
                                                                user.getFcmToken(),
                                                                "⚠️ Booking Warning",
                                                                "Please enter soon or your booking will be cancelled.");
                                        }

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

                                        waitlistService.notifyNextUser(
                                                        booking.getParkingId(),
                                                        booking.getVehicleType(),
                                                        booking.getStartTime().toLocalDate());

                                        // 🔔 REALTIME
                                        realtimeService.sendDashboardUpdate("CANCELLED");

                                        sendMessage(
                                                        booking.getUserId(),
                                                        " Booking cancelled. No refund");

                                        User user = userRepository
                                                        .findById(booking.getUserId())
                                                        .orElse(null);

                                        if (user != null &&
                                                        user.getFcmToken() != null &&
                                                        !user.getFcmToken().isEmpty()) {

                                                firebaseNotificationService.sendNotification(
                                                                user.getFcmToken(),
                                                                " Booking Cancelled",
                                                                "Your booking was automatically cancelled because you did not arrive on time.");
                                        }
                                }
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

                int graceMinutes = 15;

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository
                                .findByParkingId(parkingId);

                for (Booking b : bookings) {

                        if (!"ACTIVE".equals(b.getStatus()))
                                continue;
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

                List<Booking> bookings = bookingRepository
                                .findByParkingId(parkingId);

                for (Booking b : bookings) {

                        if (!"BOOKED".equals(b.getStatus()))
                                continue;

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

                        double rate = "TWO_WHEELER"
                                        .equals(booking.getVehicleType())
                                                        ? parking.getBikeHourlyRate()
                                                        : parking.getCarHourlyRate();

                        amount = Math.ceil(
                                        minutes / 60.0) * rate;
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

        public Booking markExitAndReturn(String bookingId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                Parking parking = parkingRepository
                                .findById(booking.getParkingId())
                                .orElseThrow();
                LocalDateTime originalEndTime = booking.getEndTime();

                Object lock = getLock(booking.getParkingId());

                synchronized (lock) {

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

                                double rate = "TWO_WHEELER"
                                                .equals(booking.getVehicleType())
                                                                ? parking.getBikeHourlyRate()
                                                                : parking.getCarHourlyRate();

                                booking.setAmount(
                                                Math.ceil(minutes / 60.0) * rate);
                        } else {

                                booking.setAmount(
                                                booking.getBookingFee());

                        }

                        booking.setStatus("COMPLETED");

                        try {

                                Booking saved = bookingRepository.save(booking);

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
        }

        public Booking markExitByVehicle( // Guard App (operations screen m2 ,m3 , m5)
                        String vehicleNumber,
                        String parkingId,
                        String paymentMode) {

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
                                booking.getBookingId());

        }

        public Booking markExitByVehicle(String vehicleNumber) {

                Booking booking = bookingRepository
                                .findTopByVehicleNumberAndStatus(vehicleNumber, "ACTIVE")
                                .orElseThrow(
                                                () -> new ResponseStatusException(
                                                                HttpStatus.BAD_REQUEST,
                                                                "No active booking found"));

                return markExitAndReturn(booking.getBookingId());
        }

        // =====================================
        // WALK-IN METHODS
        // =====================================

        public Booking createWalkin(Map<String, String> req) { // Guard App (walkin screen m1)

                String parkingId = req.get("parkingId");
                String vehicleType = req.get("vehicleType");
                String vehicleNumber = req.get("vehicleNumber");
                String phone = req.get("phoneNumber");

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
                                        .findByParkingId(parkingId)
                                        .stream()
                                        .filter(b -> "ACTIVE".equals(b.getStatus()))
                                        .filter(b -> "WALKIN".equalsIgnoreCase(b.getType()))
                                        .filter(b -> vehicleType.equals(b.getVehicleType()))
                                        .count();
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

                                // 🔥 BLOCK CURRENT SLOT ONLY
                                realtimeService.sendDashboardUpdate("ENTRY_MARKED");

                                return saved;

                        } catch (Exception e) {

                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Walk-in failed. Try again.");
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

        public List<Booking> getUserBookings(String userId) { // User App (booking screen m1 + my booking m1)
                return bookingRepository.findByUserId(userId);
        }

        public List<Booking> getUserHistory(String userId) {
                return bookingRepository.findByUserId(userId)
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

                result.put("startTime", booking.getStartTime());
                result.put("entryTime", booking.getEntryTime());
                result.put("exitTime", booking.getExitTime());

                result.put(
                                "phoneNumber",
                                user != null
                                                ? user.getPhoneNumber()
                                                : booking.getPhoneNumber());

                return result;
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
                System.out.println("Message to user " + userId + ": " + finalMessage);

                // If using WebSocket / Notification service → send here
        }

}