package com.parking.backend.service;

import java.util.ArrayList;
import java.util.HashMap;

import org.springframework.stereotype.Service;

import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.model.User;
import com.parking.backend.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Comparator;
import java.util.List;

@Service
public class GuardService {

        private final BookingRepository bookingRepository;

        private final ParkingRepository parkingRepository;

        private final UserRepository userRepository;

        private final BookingService bookingService;

        GuardService(BookingRepository bookingRepository, UserRepository userRepository, BookingService bookingService,
                        ParkingRepository parkingRepository) {
                this.userRepository = userRepository;
                this.bookingService = bookingService;
                this.parkingRepository = parkingRepository;
                this.bookingRepository = bookingRepository;
        }

        public Map<String, Object> getDashboard(String parkingId) { // Guard App (monitoring screen m3)

                List<Booking> bookings = bookingRepository.findByParkingId(parkingId);

                long active = bookings.stream()
                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                .count();

                long overtime = bookings.stream()
                                .filter(b -> "ACTIVE".equals(b.getStatus()) &&
                                                b.getEndTime() != null &&
                                                b.getEndTime().isBefore(LocalDateTime.now()))
                                .count();

                LocalDateTime now = LocalDateTime.now();

                long notEntered = bookings.stream()
                                .filter(b -> "BOOKED".equals(b.getStatus()))
                                .filter(b -> b.getEntryTime() == null)
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> !b.getStartTime().isAfter(now))
                                .count();
                long walkins = bookings.stream()
                                .filter(b -> "WALKIN".equals(b.getType()) &&
                                                "ACTIVE".equals(b.getStatus()))
                                .count();

                Map<String, Object> res = new HashMap<>();

                res.put("active", active);
                res.put("overtime", overtime);
                res.put("notEntered", notEntered);
                res.put("walkins", walkins);

                return res;
        }

        public Map<String, Object> getCapacity(String parkingId) {
                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                Map<String, Object> res = new HashMap<>();

                res.put(
                                "carsAvailable",
                                parking.getFourWheelerAvailable());

                res.put(
                                "carsCapacity",
                                parking.getFourWheelerCapacity());

                res.put(
                                "bikesAvailable",
                                parking.getTwoWheelerAvailable());

                res.put(
                                "bikesCapacity",
                                parking.getTwoWheelerCapacity());

                return res;
        }

        public List<Map<String, Object>> getUpcomingArrivals( // Guard App (monitoring screen m5)
                        String parkingId) {

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime next30Minutes = now.plusMinutes(30);

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository
                                .findByParkingIdAndStatusOrderByStartTimeAsc(
                                                parkingId,
                                                "BOOKED");

                for (Booking b : bookings) {

                        if (b.getStartTime() == null) {
                                continue;
                        }

                        if (b.getStartTime().isBefore(now)) {
                                continue;
                        }

                        if (b.getStartTime().isAfter(next30Minutes)) {
                                continue;
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
                        map.put("vehicleType", b.getVehicleType());
                        map.put("phoneNumber", phone);
                        map.put("startTime", b.getStartTime());
                        map.put("paymentStatus", b.getPaymentStatus());
                        map.put("type", b.getType());

                        result.add(map);
                }

                return result;
        }

        public List<Booking> getTodayLogs(
                        String parkingId) {

                return bookingRepository
                                .findByParkingId(parkingId);
        }

        public Map<String, Object> getRevenueStats( // Guard App (monitoring screen m1)
                        String parkingId) {

                List<Booking> bookings = bookingRepository
                                .findByParkingId(parkingId);

                LocalDate today = LocalDate.now();

                double cashCollection = 0;
                double onlineCollection = 0;
                double revenue = 0;

                long vehicles = 0;

                for (Booking b : bookings) {

                        if (!"COMPLETED".equals(b.getStatus())) {
                                continue;
                        }

                        if (b.getExitTime() == null) {
                                continue;
                        }

                        if (!b.getExitTime()
                                        .toLocalDate()
                                        .equals(today)) {
                                continue;
                        }

                        vehicles++;

                        double collected = 0;

                        if ("WALKIN".equalsIgnoreCase(b.getType())) {
                                collected = b.getAmount();
                        } else {
                                collected = b.getCollectedFineAmount();
                        }

                        if ("CASH".equalsIgnoreCase(b.getPaymentMode())) {
                                cashCollection += collected;
                        }

                        if ("ONLINE".equalsIgnoreCase(b.getPaymentMode())) {
                                onlineCollection += collected;
                        }

                        revenue += collected;
                }

                Map<String, Object> res = new HashMap<>();

                res.put("revenue", revenue);
                res.put("vehicles", vehicles);
                res.put("cashCollection", cashCollection);
                res.put("onlineCollection", onlineCollection);

                return res;
        }

        public List<Map<String, Object>> getTodayActivity( // Guard App (monitoring screen m6 + today activity m1)
                        String parkingId) {

                List<Booking> bookings = bookingRepository
                                .findByParkingId(parkingId);

                LocalDate today = LocalDate.now();

                List<Map<String, Object>> result = new ArrayList<>();

                for (Booking b : bookings) {

                        // ENTRY
                        if (b.getEntryTime() != null &&
                                        b.getEntryTime()
                                                        .toLocalDate()
                                                        .equals(today)) {

                                Map<String, Object> map = new HashMap<>();

                                map.put("type", "ENTRY");
                                map.put("vehicleNumber",
                                                b.getVehicleNumber());

                                map.put("vehicleType",
                                                b.getVehicleType());

                                map.put("time",
                                                b.getEntryTime());
                                map.put("bookingType",
                                                b.getType());
                                map.put(
                                                "phoneNumber",
                                                b.getPhoneNumber());

                                result.add(map);
                        }

                        // EXIT
                        if (b.getExitTime() != null &&
                                        b.getExitTime()
                                                        .toLocalDate()
                                                        .equals(today)) {

                                Map<String, Object> map = new HashMap<>();

                                map.put("type", "EXIT");
                                map.put("vehicleNumber",
                                                b.getVehicleNumber());

                                map.put("vehicleType",
                                                b.getVehicleType());

                                map.put("time",
                                                b.getExitTime());

                                map.put("amount",
                                                b.getAmount());
                                map.put(
                                                "phoneNumber",
                                                b.getPhoneNumber());

                                map.put(
                                                "bookingType",
                                                b.getType());

                                result.add(map);
                        }
                }

                result.sort((a, b) ->

                ((LocalDateTime) b.get("time"))
                                .compareTo(
                                                (LocalDateTime) a.get("time")));

                return result;
        }

        public List<Booking> getTodayBookings(
                        String parkingId) {

                LocalDate today = LocalDate.now();

                return bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> b.getStartTime()
                                                .toLocalDate()
                                                .equals(today))
                                .sorted(
                                                Comparator.comparing(
                                                                Booking::getStartTime)
                                                                .reversed())
                                .toList();
        }

        public List<Map<String, Object>> getInsideVehicles(
                        String parkingId,
                        String vehicleType,
                        String sourceType,
                        String status) {

                List<Map<String, Object>> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository.findByParkingId(parkingId);

                for (Booking b : bookings) {

                        // Status filter (ACTIVE / BOOKED)
                        if (!status.equalsIgnoreCase(b.getStatus())) {
                                continue;
                        }

                        // Vehicle type filter
                        if (!vehicleType.equalsIgnoreCase(b.getVehicleType())) {
                                continue;
                        }

                        // BOOKING / WALKIN filter
                        if (!sourceType.equalsIgnoreCase(b.getType())) {
                                continue;
                        }

                        // Show only today's bookings in BOOKED tab
                        if ("BOOKED".equalsIgnoreCase(status)) {

                                if (b.getStartTime() == null) {
                                        continue;
                                }

                                if (!b.getStartTime().toLocalDate().equals(LocalDate.now())) {
                                        continue;
                                }
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
                        map.put("vehicleType", b.getVehicleType());
                        map.put("phoneNumber", phone);
                        map.put("type", b.getType());
                        map.put("entryTime", b.getEntryTime());
                        map.put("startTime", b.getStartTime());
                        map.put("amount", b.getAmount());
                        map.put("fineAmount", b.getFineAmount());
                        map.put("parkingName", b.getParkingName());
                        map.put("expectedExitTime", b.getEndTime());
                        map.put("paymentStatus", b.getPaymentStatus());

                        result.add(map);
                }

                return result;
        }

        public Map<String, Object> getRevenueDetails( // Guard App (revenue details m1)
                        String parkingId) {

                LocalDate today = LocalDate.now();

                List<Booking> bookings = bookingRepository
                                .findByParkingId(parkingId);

                double cashCollection = 0;
                double onlineCollection = 0;

                List<Map<String, Object>> transactions = new ArrayList<>();

                for (Booking b : bookings) {

                        if (!"COMPLETED".equals(
                                        b.getStatus())) {
                                continue;
                        }

                        if (b.getExitTime() == null) {
                                continue;
                        }

                        if (!b.getExitTime()
                                        .toLocalDate()
                                        .equals(today)) {
                                continue;
                        }

                        double total = 0;

                        if ("WALKIN".equalsIgnoreCase(b.getType())) {

                                total = b.getAmount();

                        } else {

                                total = b.getCollectedFineAmount();
                        }

                        if ("CASH".equals(
                                        b.getPaymentMode())) {

                                cashCollection += total;

                        } else if ("ONLINE".equals(
                                        b.getPaymentMode())) {

                                onlineCollection += total;
                        }

                        Map<String, Object> tx = new HashMap<>();

                        tx.put(
                                        "vehicleNumber",
                                        b.getVehicleNumber());

                        tx.put(
                                        "type",
                                        b.getType());

                        tx.put(
                                        "amount",
                                        "WALKIN".equalsIgnoreCase(
                                                        b.getType())
                                                                        ? b.getAmount()
                                                                        : 0);

                        tx.put(
                                        "fine",
                                        b.getCollectedFineAmount());

                        tx.put(
                                        "total",
                                        total);

                        tx.put(
                                        "paymentMode",
                                        b.getPaymentMode());

                        tx.put(
                                        "time",
                                        b.getExitTime());

                        tx.put(
                                        "bookingId",
                                        b.getBookingId());

                        transactions.add(tx);
                }

                Map<String, Object> result = new HashMap<>();

                result.put(
                                "cashCollection",
                                cashCollection);

                result.put(
                                "onlineCollection",
                                onlineCollection);

                result.put(
                                "totalCollection",
                                cashCollection + onlineCollection);

                result.put(
                                "transactions",
                                transactions);

                return result;
        }

        public Map<String, Object> getCapacityBreakdown(
                        String parkingId) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow(() -> new RuntimeException("Parking not found"));

                Map<String, Object> result = new HashMap<>();

                result.put(
                                "cars",
                                buildGuardCapacity(
                                                parking,
                                                parkingId,
                                                "FOUR_WHEELER"));

                result.put(
                                "bikes",
                                buildGuardCapacity(
                                                parking,
                                                parkingId,
                                                "TWO_WHEELER"));

                return result;
        }

        private Map<String, Object> buildGuardCapacity(
                        Parking parking,
                        String parkingId,
                        String vehicleType) {

                int totalCapacity = "TWO_WHEELER".equals(vehicleType)
                                ? parking.getTwoWheelerCapacity()
                                : parking.getFourWheelerCapacity();

                int bookingCapacity = bookingService.getBookingCapacity(
                                parking,
                                vehicleType);

                int walkinCapacity = totalCapacity - bookingCapacity;

                LocalDate today = LocalDate.now();
                long bookedCount = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> "BOOKED".equals(b.getStatus()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .filter(b -> b.getStartTime() != null)
                                .filter(b -> b.getStartTime()
                                                .toLocalDate()
                                                .equals(today))
                                .count();

                long activeCount = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                .filter(b -> !"WALKIN".equalsIgnoreCase(b.getType()))
                                .count();
                long walkinActive = bookingRepository
                                .findByParkingId(parkingId)
                                .stream()
                                .filter(b -> vehicleType.equals(b.getVehicleType()))
                                .filter(b -> "ACTIVE".equals(b.getStatus()))
                                .filter(b -> "WALKIN".equalsIgnoreCase(b.getType()))
                                .count();

                Map<String, Object> map = new HashMap<>();

                map.put("bookingCapacity", bookingCapacity);

                map.put("bookedCount", bookedCount);

                map.put("activeCount", activeCount);

                map.put("bookingOccupied",
                                bookedCount + activeCount);

                map.put("remainingBooking",
                                Math.max(0,
                                                bookingCapacity - (bookedCount + activeCount)));

                map.put("walkinCapacity", walkinCapacity);

                map.put("walkinActive", walkinActive);

                map.put("remainingWalkin",
                                Math.max(0,
                                                walkinCapacity - (int) walkinActive));

                return map;
        }
}