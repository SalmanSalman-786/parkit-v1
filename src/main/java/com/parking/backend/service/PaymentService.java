package com.parking.backend.service;

import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import com.razorpay.Utils;
import com.razorpay.Refund;

import com.parking.backend.dto.PaymentStatus;
import com.razorpay.Payment;
import java.util.List;

import com.razorpay.PaymentLink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PaymentService {

        private final BookingRepository bookingRepository;
        private final ParkingRepository parkingRepository;
        private final ParkingTariffService parkingTariffService;

        // 🔥 TEST KEYS
        @Value("${razorpay.key.id}")
        private String keyId;

        @Value("${razorpay.key.secret}")
        private String keySecret;

        public PaymentService(
                        BookingRepository bookingRepository,
                        ParkingRepository parkingRepository,
                        ParkingTariffService parkingTariffService) {

                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
                this.parkingTariffService = parkingTariffService;
        }

        private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

        public String createOrder(String bookingId) { // User App (booking screen m8)

                try {

                        Booking booking = bookingRepository
                                        .findByBookingId(bookingId)
                                        .orElseThrow(() -> new RuntimeException("Booking not found"));

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);

                        JSONObject options = new JSONObject();

                        // Razorpay uses paise
                        options.put("amount", (int) Math.round(booking.getAmount() * 100));

                        options.put("currency", "INR");

                        // Use bookingId as receipt for easy tracking
                        options.put("receipt", booking.getBookingId());

                        JSONObject notes = new JSONObject();

                        notes.put("bookingId", booking.getBookingId());

                        notes.put("parkingId", booking.getParkingId());

                        notes.put("userId", booking.getUserId());

                        options.put("notes", notes);

                        Order order = client.orders.create(options);

                        // Save Razorpay Order ID BEFORE payment
                        booking.setRazorpayOrderId(order.get("id"));

                        bookingRepository.save(booking);

                        return order.toString();

                } catch (Exception e) {
                        throw new RuntimeException("Failed to create payment order");
                }
        }

        public String createOrder(double amount) { // User App (booking screen m8) + Guard App (operations screen m6)

                try {

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);

                        JSONObject options = new JSONObject();

                        // Razorpay uses paise
                        options.put("amount", (int) (amount * 100));

                        options.put("currency", "INR");

                        options.put("receipt", "rcpt_" + System.currentTimeMillis());

                        Order order = client.orders.create(options);

                        return order.toString();

                } catch (Exception e) {
                        throw new RuntimeException("Failed to create payment order");
                }
        }

        public boolean verifyPayment( // User App (booking screen m4)
                        String orderId,
                        String paymentId,
                        String signature) {

                try {

                        JSONObject options = new JSONObject();

                        options.put("razorpay_order_id", orderId);

                        options.put("razorpay_payment_id", paymentId);

                        options.put("razorpay_signature", signature);

                        return Utils.verifyPaymentSignature(
                                        options,
                                        keySecret);

                } catch (Exception e) {

                        return false;
                }
        }

        public String refundPayment(
                        String paymentId,
                        double amount) {

                try {

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);

                        JSONObject request = new JSONObject();

                        request.put("amount",
                                        (int) (amount * 100));

                        Refund refund = client.payments.refund(
                                        paymentId,
                                        request);

                        return refund.get("id");

                } catch (Exception e) {

                        log.error("Refund failed.", e);

                        throw new RuntimeException(
                                        "Refund failed: " + e.getMessage());
                }
        }

        public boolean verifyFinePayment(
                        String bookingId,
                        String orderId,
                        String paymentId,
                        String signature) {

                boolean verified = verifyPayment(
                                orderId,
                                paymentId,
                                signature);

                if (!verified) {
                        return false;
                }

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Booking not found"));

                booking.setFinePaid(true);

                booking.setFinePaymentMode("ONLINE");

                booking.setFinePaymentId(paymentId);

                booking.setFinePaymentTime(
                                LocalDateTime.now());

                bookingRepository.save(booking);

                return true;
        }

        public boolean verifyExitPayment( // Guard App (operations screen m4)
                        String bookingId,
                        String orderId,
                        String paymentId,
                        String signature) {

                boolean verified = verifyPayment(
                                orderId,
                                paymentId,
                                signature);

                if (!verified) {
                        return false;
                }

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Booking not found"));

                // Store payment info
                completeExitPayment(
                                booking,
                                paymentId);

                return true;
        }

        public void completeExitPayment(
                        Booking booking,
                        String paymentId) {

                booking.setPaymentStatus("PAID");

                booking.setPaymentMode("ONLINE");

                booking.setRazorpayPaymentId(paymentId);

                booking.setPaymentTime(LocalDateTime.now());

                if (booking.getFineAmount() > 0) {

                        booking.setFinePaid(true);

                        booking.setFinePaymentMode("ONLINE");

                        booking.setFinePaymentId(paymentId);

                        booking.setFinePaymentTime(LocalDateTime.now());

                        booking.setCollectedFineAmount(
                                        booking.getFineAmount());
                }

                bookingRepository.save(booking);
        }

        public PaymentStatus checkPayment(String orderId) {

                try {

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);

                        List<Payment> payments = client.orders.fetchPayments(orderId);

                        for (Payment payment : payments) {

                                String status = payment.get("status");

                                if ("captured".equalsIgnoreCase(status)) {

                                        return new PaymentStatus(
                                                        true,
                                                        payment.get("id"));
                                }
                        }

                        return new PaymentStatus(false, null);

                } catch (Exception e) {

                        throw new RuntimeException(
                                        "Unable to verify payment with Razorpay",
                                        e);
                }
        }

        public String createExitPaymentLink(String bookingId) {

                try {

                        Booking booking = bookingRepository
                                        .findByBookingId(bookingId)
                                        .orElseThrow(() -> new RuntimeException("Booking not found"));

                        double amount = 0;

                        double fine = booking.getFineAmount();

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {

                                long minutes = Duration
                                                .between(
                                                                booking.getEntryTime(),
                                                                LocalDateTime.now())
                                                .toMinutes();

                                Parking parking = parkingRepository
                                                .findById(booking.getParkingId())
                                                .orElseThrow();

                                amount = parkingTariffService.calculatePrice(
                                                parking.getId(),
                                                ParkingTariffService.WALKIN,
                                                booking.getVehicleType(),
                                                minutes);
                        }

                        double total;

                        if ("WALKIN".equalsIgnoreCase(booking.getType())) {

                                total = amount + fine;

                        } else {

                                total = fine;
                        }

                        if (total <= 0) {
                                throw new RuntimeException("Invalid amount");
                        }

                        // Reuse payment link only if the amount has not changed
                        if (booking.getPaymentLinkId() != null
                                        && "CREATED".equals(booking.getPaymentLinkStatus())
                                        && booking.getPaymentLinkUrl() != null
                                        && booking.getPaymentLinkAmount() != null
                                        && Double.compare(booking.getPaymentLinkAmount(), total) == 0) {

                                log.debug("Reusing existing payment link for booking {}", bookingId);

                                JSONObject response = new JSONObject();

                                response.put("id", booking.getPaymentLinkId());
                                response.put("short_url", booking.getPaymentLinkUrl());
                                response.put("status", "created");

                                return response.toString();
                        }

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);

                        JSONObject request = new JSONObject();
                        
                        request.put("amount", (int) (total * 100));

                        request.put("currency", "INR");

                        request.put("accept_partial", false);

                        request.put("description", "Parking Exit Payment");

                        String reference = "EX" + System.currentTimeMillis();

                        request.put("reference_id", reference);

                        request.put("notify", new JSONObject()
                                        .put("sms", false)
                                        .put("email", false));

                        request.put("reminder_enable", false);

                        PaymentLink paymentLink = client.paymentLink.create(request);

                        log.debug("Payment link created for booking {}", bookingId);

                        booking.setPaymentLinkId(paymentLink.get("id"));
                        booking.setPaymentLinkUrl(paymentLink.get("short_url"));

                        booking.setPaymentLinkStatus("CREATED");
                        booking.setPaymentLinkReference(reference);
                        booking.setPaymentLinkAmount(total);

                        bookingRepository.save(booking);

                        return paymentLink.toString();

                } catch (Exception e) {

                        log.error("Failed to create payment link.", e);

                        throw new RuntimeException(
                                        "Failed to create payment link: " + e.getMessage(),
                                        e);
                }
        }

}