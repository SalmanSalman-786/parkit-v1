package com.parking.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.parking.backend.dto.AvailabilitySummaryDto;
import com.parking.backend.dto.ParkingRevenueDto;
import com.parking.backend.dto.RevenueSummaryResponse;
import com.parking.backend.dto.RevenueTransactionDto;
import com.parking.backend.dto.VehicleSlotDto;
import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;

@Service
public class AdminService {

        private final BookingRepository bookingRepository;

        private final ParkingRepository parkingRepository;

        AdminService(BookingRepository bookingRepository, ParkingRepository parkingRepository) {
                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
        }

        public RevenueSummaryResponse getRevenueSummary(LocalDate date) {

                RevenueSummaryResponse res = new RevenueSummaryResponse();

                res.setDate(date);

                List<Booking> completedBookings = bookingRepository.findByStatus("COMPLETED");
                List<Booking> cancelledBookings = bookingRepository.findByStatus("CANCELLED");

                double bookingFee = 0;
                double bookingFeeRefund = 0;

                double assuranceDeposit = 0;
                double assuranceDepositRefund = 0;

                double walkinRevenue = 0;
                double fineRevenue = 0;

                long transactionCount = 0;

                // ===========================
                // COMPLETED BOOKINGS
                // ===========================
                for (Booking booking : completedBookings) {

                        if (booking.getExitTime() == null) {
                                continue;
                        }

                        if (!booking.getExitTime().toLocalDate().equals(date)) {
                                continue;
                        }

                        transactionCount++;

                        if ("BOOKING".equalsIgnoreCase(booking.getType())) {

                                bookingFee += booking.getBookingFee();

                                assuranceDeposit += booking.getAssuranceDeposit();

                                assuranceDepositRefund += booking.getAssuranceDepositRefund();
                        }

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {

                                walkinRevenue += booking.getAmount();
                        }

                        if (booking.isFinePaid()) {

                                fineRevenue += booking.getCollectedFineAmount();
                        }
                }

                // ===========================
                // CANCELLED BOOKINGS
                // ===========================
                for (Booking booking : cancelledBookings) {

                        if (booking.getRefundTime() == null) {
                                continue;
                        }

                        if (!booking.getRefundTime().toLocalDate().equals(date)) {
                                continue;
                        }

                        transactionCount++;

                        bookingFee += booking.getBookingFee();

                        assuranceDeposit += booking.getAssuranceDeposit();

                        bookingFeeRefund += booking.getBookingFeeRefund();

                        assuranceDepositRefund += booking.getAssuranceDepositRefund();
                }

                // ===========================
                // NET REVENUE
                // ===========================
                double netRevenue = bookingFee
                                + assuranceDeposit
                                + walkinRevenue
                                + fineRevenue
                                - bookingFeeRefund
                                - assuranceDepositRefund;

                res.setBookingFee(bookingFee);
                res.setBookingFeeRefund(bookingFeeRefund);

                res.setAssuranceDeposit(assuranceDeposit);
                res.setAssuranceDepositRefund(assuranceDepositRefund);

                res.setWalkinRevenue(walkinRevenue);

                res.setFineRevenue(fineRevenue);

                res.setNetRevenue(netRevenue);

                res.setTransactionCount(transactionCount);

                return res;
        }

        private boolean matchesFilter(
                        RevenueTransactionDto dto,
                        String filter) {

                if (filter == null ||
                                filter.isBlank() ||
                                filter.equalsIgnoreCase("ALL")) {

                        return true;
                }

                if (filter.equalsIgnoreCase("ONLINE")) {

                        return "ONLINE".equalsIgnoreCase(
                                        dto.getPaymentMode());
                }

                if (filter.equalsIgnoreCase("CASH")) {

                        return "CASH".equalsIgnoreCase(
                                        dto.getPaymentMode());
                }

                if (filter.equalsIgnoreCase("FINE")) {

                        return "FINE".equalsIgnoreCase(
                                        dto.getTransactionType());
                }

                if (filter.equalsIgnoreCase("REFUND")) {

                        return "REFUND".equalsIgnoreCase(
                                        dto.getTransactionType());
                }

                return true;
        }

        public List<RevenueTransactionDto> getRevenueTransactions( // Admin website (Revenue transactions m1)
                        LocalDate date,
                        String filter,
                        String parkingId) {

                List<RevenueTransactionDto> result = new ArrayList<>();

                List<Booking> bookings = bookingRepository.findByStatus("COMPLETED");
                List<Booking> cancelledBookings = bookingRepository.findByStatus("CANCELLED");

                for (Booking booking : bookings) {

                        if (booking.getExitTime() == null) {
                                continue;
                        }

                        if (!booking.getExitTime()
                                        .toLocalDate()
                                        .equals(date)) {
                                continue;
                        }

                        if (parkingId != null &&
                                        !parkingId.isBlank() &&
                                        !parkingId.equals(
                                                        booking.getParkingId())) {

                                continue;
                        }

                        // =====================
                        // BOOKING FEE
                        // =====================

                        if ("BOOKING".equalsIgnoreCase(booking.getType())
                                        && booking.getBookingFee() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());

                                dto.setVehicleNumber(booking.getVehicleNumber());

                                dto.setDateTime(booking.getPaymentTime());

                                dto.setTransactionType("BOOKING_FEE");

                                dto.setPaymentMode(
                                                "PREPAID".equalsIgnoreCase(booking.getPaymentMode())
                                                                ? "PREPAID (ONLINE)"
                                                                : booking.getPaymentMode());

                                dto.setAmount(booking.getBookingFee());

                                dto.setParkingId(booking.getParkingId());

                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // ASSURANCE DEPOSIT
                        // =====================

                        if ("BOOKING".equalsIgnoreCase(booking.getType())
                                        && booking.getAssuranceDeposit() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());

                                dto.setVehicleNumber(booking.getVehicleNumber());

                                dto.setDateTime(booking.getPaymentTime());

                                dto.setTransactionType("ASSURANCE_DEPOSIT");

                                dto.setPaymentMode(
                                                "PREPAID".equalsIgnoreCase(booking.getPaymentMode())
                                                                ? "PREPAID (ONLINE)"
                                                                : booking.getPaymentMode());

                                dto.setAmount(booking.getAssuranceDeposit());

                                dto.setParkingId(booking.getParkingId());

                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // FINE
                        // =====================

                        if (booking.isFinePaid()
                                        && booking.getCollectedFineAmount() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());

                                dto.setVehicleNumber(booking.getVehicleNumber());

                                dto.setDateTime(booking.getFinePaymentTime());

                                dto.setTransactionType("FINE");

                                dto.setPaymentMode(booking.getFinePaymentMode());

                                dto.setAmount(booking.getCollectedFineAmount());

                                dto.setParkingId(booking.getParkingId());

                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // ASSURANCE DEPOSIT REFUND
                        // =====================

                        if (booking.getAssuranceDepositRefund() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());

                                dto.setVehicleNumber(booking.getVehicleNumber());

                                dto.setDateTime(booking.getDepositRefundTime());

                                dto.setTransactionType("ASSURANCE_DEPOSIT_REFUND");

                                dto.setPaymentMode(booking.getPaymentMode());

                                dto.setAmount(-booking.getAssuranceDepositRefund());

                                dto.setParkingId(booking.getParkingId());

                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // WALK-IN
                        // =====================

                        if ("WALKIN".equalsIgnoreCase(booking.getType())
                                        && booking.getAmount() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());

                                dto.setVehicleNumber(booking.getVehicleNumber());

                                dto.setDateTime(booking.getExitTime());

                                dto.setTransactionType("WALKIN");

                                dto.setPaymentMode(booking.getPaymentMode());

                                dto.setAmount(booking.getAmount());

                                dto.setParkingId(booking.getParkingId());

                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }
                }

                for (Booking booking : cancelledBookings) {

                        if (booking.getRefundTime() == null) {
                                continue;
                        }

                        if (!booking.getRefundTime().toLocalDate().equals(date)) {
                                continue;
                        }

                        if (parkingId != null
                                        && !parkingId.isBlank()
                                        && !parkingId.equals(booking.getParkingId())) {
                                continue;
                        }

                        // =====================
                        // BOOKING FEE
                        // =====================

                        if (booking.getBookingFee() > 0
                                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());
                                dto.setVehicleNumber(booking.getVehicleNumber());
                                dto.setDateTime(booking.getPaymentTime());
                                dto.setTransactionType("BOOKING_FEE");
                                dto.setPaymentMode(booking.getPaymentMode());
                                dto.setAmount(booking.getBookingFee());
                                dto.setParkingId(booking.getParkingId());
                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // ASSURANCE DEPOSIT
                        // =====================

                        if (booking.getAssuranceDeposit() > 0
                                        && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());
                                dto.setVehicleNumber(booking.getVehicleNumber());
                                dto.setDateTime(booking.getPaymentTime());
                                dto.setTransactionType("ASSURANCE_DEPOSIT");
                                dto.setPaymentMode(booking.getPaymentMode());
                                dto.setAmount(booking.getAssuranceDeposit());
                                dto.setParkingId(booking.getParkingId());
                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // BOOKING FEE REFUND
                        // =====================

                        if (booking.getBookingFeeRefund() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());
                                dto.setVehicleNumber(booking.getVehicleNumber());
                                dto.setDateTime(booking.getRefundTime());
                                dto.setTransactionType("BOOKING_FEE_REFUND");
                                dto.setPaymentMode(booking.getPaymentMode());
                                dto.setAmount(-booking.getBookingFeeRefund());
                                dto.setParkingId(booking.getParkingId());
                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }

                        // =====================
                        // ASSURANCE DEPOSIT REFUND
                        // =====================

                        if (booking.getAssuranceDepositRefund() > 0) {

                                RevenueTransactionDto dto = new RevenueTransactionDto();

                                dto.setBookingId(booking.getBookingId());
                                dto.setVehicleNumber(booking.getVehicleNumber());
                                dto.setDateTime(booking.getRefundTime());
                                dto.setTransactionType("ASSURANCE_DEPOSIT_REFUND");
                                dto.setPaymentMode(booking.getPaymentMode());
                                dto.setAmount(-booking.getAssuranceDepositRefund());
                                dto.setParkingId(booking.getParkingId());
                                dto.setParkingName(booking.getParkingName());

                                if (matchesFilter(dto, filter)) {
                                        result.add(dto);
                                }
                        }
                }

                result.sort(
                                Comparator.comparing(
                                                RevenueTransactionDto::getDateTime,
                                                Comparator.nullsLast(Comparator.reverseOrder())));

                return result;
        }

        public List<ParkingRevenueDto> getParkingRevenue(LocalDate date) {

                List<Booking> completedBookings = bookingRepository.findByStatus("COMPLETED");
                List<Booking> cancelledBookings = bookingRepository.findByStatus("CANCELLED");

                Map<String, ParkingRevenueDto> revenueMap = new HashMap<>();

                // ===========================
                // COMPLETED BOOKINGS
                // ===========================
                for (Booking booking : completedBookings) {

                        if (booking.getExitTime() == null) {
                                continue;
                        }

                        if (!booking.getExitTime().toLocalDate().equals(date)) {
                                continue;
                        }

                        String parkingId = booking.getParkingId();

                        ParkingRevenueDto dto = revenueMap.getOrDefault(
                                        parkingId,
                                        new ParkingRevenueDto());

                        dto.setParkingId(parkingId);
                        dto.setParkingName(booking.getParkingName());

                        double revenue = dto.getRevenue();

                        if ("BOOKING".equalsIgnoreCase(booking.getType())) {

                                revenue += booking.getBookingFee();
                                revenue += booking.getAssuranceDeposit();
                                revenue -= booking.getAssuranceDepositRefund();
                        }

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {

                                revenue += booking.getAmount();
                        }

                        if (booking.isFinePaid()) {

                                revenue += booking.getCollectedFineAmount();
                        }

                        dto.setRevenue(revenue);

                        revenueMap.put(parkingId, dto);
                }

                // ===========================
                // CANCELLED BOOKINGS
                // ===========================
                for (Booking booking : cancelledBookings) {

                        if (booking.getRefundTime() == null) {
                                continue;
                        }

                        if (!booking.getRefundTime().toLocalDate().equals(date)) {
                                continue;
                        }

                        String parkingId = booking.getParkingId();

                        ParkingRevenueDto dto = revenueMap.getOrDefault(
                                        parkingId,
                                        new ParkingRevenueDto());

                        dto.setParkingId(parkingId);
                        dto.setParkingName(booking.getParkingName());

                        double revenue = dto.getRevenue();

                        revenue += booking.getBookingFee();
                        revenue += booking.getAssuranceDeposit();

                        revenue -= booking.getBookingFeeRefund();
                        revenue -= booking.getAssuranceDepositRefund();

                        dto.setRevenue(revenue);

                        revenueMap.put(parkingId, dto);
                }

                List<ParkingRevenueDto> result = new ArrayList<>(revenueMap.values());

                result.sort((a, b) -> Double.compare(
                                b.getRevenue(),
                                a.getRevenue()));

                return result;
        }

        public List<Map<String, Object>> getLast7DaysRevenue() { // Admin Website (dashboard m2)

                List<Map<String, Object>> result = new ArrayList<>();

                for (int i = 6; i >= 0; i--) {

                        LocalDate date = LocalDate.now().minusDays(i);

                        RevenueSummaryResponse revenue = getRevenueSummary(date);

                        Map<String, Object> day = new HashMap<>();

                        day.put("date", date.toString());

                        day.put(
                                        "revenue",
                                        revenue.getNetRevenue());

                        result.add(day);
                }

                return result;
        }

        public AvailabilitySummaryDto getAvailabilitySummary(
                        String parkingId,
                        LocalDateTime startTime,
                        LocalDateTime endTime,
                        String vehicleType) {

                Parking parking = parkingRepository
                                .findById(parkingId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Parking not found"));

                List<Booking> bookings = bookingRepository.findAll();

                List<Booking> matching = bookings.stream()
                                .filter(b -> parkingId.equals(
                                                b.getParkingId()))
                                .filter(b -> List.of(
                                                "BOOKED",
                                                "ACTIVE")
                                                .contains(
                                                                b.getStatus()))
                                .filter(b -> b.getStartTime()
                                                .isBefore(endTime)
                                                &&
                                                b.getEndTime()
                                                                .isAfter(startTime))
                                .filter(b -> vehicleType == null
                                                ||
                                                vehicleType.isBlank()
                                                ||
                                                vehicleType.equals(
                                                                b.getVehicleType()))
                                .toList();

                AvailabilitySummaryDto dto = new AvailabilitySummaryDto();

                dto.setParkingId(parkingId);

                dto.setParkingName(
                                parking.getName());

                int capacity = "TWO_WHEELER".equals(vehicleType)
                                ? parking.getTwoWheelerCapacity()
                                : parking.getFourWheelerCapacity();

                dto.setTotalCapacity(capacity);

                long booked = matching.stream()
                                .filter(b -> "BOOKED".equals(
                                                b.getStatus()))
                                .count();

                long active = matching.stream()
                                .filter(b -> "ACTIVE".equals(
                                                b.getStatus()))
                                .count();

                dto.setBookedCount(booked);

                dto.setActiveCount(active);

                dto.setAvailableCount(
                                capacity -
                                                matching.size());

                dto.setStatus(
                                matching.size() >= capacity
                                                ? "FULL"
                                                : "AVAILABLE");

                List<VehicleSlotDto> vehicles = matching.stream()
                                .map(b -> {

                                        VehicleSlotDto v = new VehicleSlotDto();

                                        v.setBookingId(
                                                        b.getBookingId());

                                        v.setVehicleNumber(
                                                        b.getVehicleNumber());

                                        v.setPhoneNumber(
                                                        b.getPhoneNumber());

                                        v.setStatus(
                                                        b.getStatus());

                                        v.setPaymentMode(
                                                        b.getPaymentMode());

                                        v.setStartTime(
                                                        b.getStartTime());

                                        v.setEndTime(
                                                        b.getEndTime());

                                        return v;

                                })
                                .toList();

                dto.setVehicles(vehicles);

                return dto;
        }

}
