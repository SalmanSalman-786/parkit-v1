package com.parking.backend.controller;

import com.parking.backend.dto.WebhookResult;
import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.service.PaymentService;
import com.parking.backend.service.WebhookService;
import com.parking.backend.dto.WebhookResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

        private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

        private final PaymentService paymentService;
        private final BookingRepository bookingRepository;
        private final ParkingRepository parkingRepository;
        private final WebhookService webhookService;

        public PaymentController(
                        PaymentService paymentService,
                        BookingRepository bookingRepository,
                        ParkingRepository parkingRepository,
                        WebhookService webhookService) {

                this.paymentService = paymentService;
                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
                this.webhookService = webhookService;
        }

        @PostMapping("/create-order")
        public String createOrder(
                        @RequestParam String bookingId) {

                return paymentService.createOrder(
                                bookingId);
        }

        @PostMapping("/verify") // User App (booking screen m4)
        public boolean verifyPayment(
                        @RequestParam String razorpay_order_id,
                        @RequestParam String razorpay_payment_id,
                        @RequestParam String razorpay_signature) {

                return paymentService.verifyPayment(
                                razorpay_order_id,
                                razorpay_payment_id,
                                razorpay_signature);
        }

        @PostMapping("/webhook")
        public String handleWebhook(
                        @RequestBody String payload,
                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

                if (signature == null || signature.isBlank()) {
                        throw new RuntimeException("Missing webhook signature");
                }

                boolean verified = webhookService.verifyWebhookSignature(
                                payload,
                                signature);

                if (!verified) {
                        throw new RuntimeException("Invalid webhook signature");
                }

                log.info("✅ Razorpay webhook verified");

                WebhookResult result = webhookService.processWebhook(payload);

                if (result.isRetry()) {
                        throw new RuntimeException(
                                        result.getMessage());
                }

                return result.getMessage();
        }

        @PostMapping("/create-fine-order")
        public String createFineOrder(
                        @RequestParam String bookingId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Booking not found"));

                if (booking.getFineAmount() <= 0) {
                        throw new RuntimeException(
                                        "No fine available");
                }

                String order = paymentService
                                .createOrder(
                                                booking.getFineAmount());

                JSONObject json = new JSONObject(order);

                booking.setFineOrderId(
                                json.getString("id"));

                bookingRepository.save(booking);

                return order;
        }

        @PostMapping("/verify-fine-payment")
        public boolean verifyFinePayment(
                        @RequestParam String bookingId,
                        @RequestParam String orderId,
                        @RequestParam String paymentId,
                        @RequestParam String signature) {

                return paymentService.verifyFinePayment(
                                bookingId,
                                orderId,
                                paymentId,
                                signature);
        }

        @PostMapping("/create-exit-order") // Guard App (operations screen m6)
        public String createExitOrder(
                        @RequestParam String bookingId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(
                                                () -> new RuntimeException(
                                                                "Booking not found"));

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

                        double rate = "TWO_WHEELER".equals(booking.getVehicleType())
                                        ? parking.getBikeHourlyRate()
                                        : parking.getCarHourlyRate();

                        amount = Math.ceil(
                                        minutes / 60.0) * rate;

                }

                double total;

                if ("WALKIN".equalsIgnoreCase(
                                booking.getType())) {

                        total = amount + fine;

                } else {

                        // Booking -> only fine
                        total = fine;
                }

                if (total <= 0) {
                        throw new RuntimeException(
                                        "Invalid amount");
                }

                String order = paymentService.createOrder(total);

                JSONObject json = new JSONObject(order);

                booking.setFineOrderId(
                                json.getString("id"));

                bookingRepository.save(booking);

                return order;
        }

        @PostMapping("/verify-exit-payment") // Guard App (operations screen m4)
        public boolean verifyExitPayment(
                        @RequestParam String bookingId,
                        @RequestParam String orderId,
                        @RequestParam String paymentId,
                        @RequestParam String signature) {

                return paymentService.verifyExitPayment(
                                bookingId,
                                orderId,
                                paymentId,
                                signature);
        }

        @PostMapping("/create-exit-payment-link")
        public String createExitPaymentLink(
                        @RequestParam String bookingId) {

                return paymentService.createExitPaymentLink(bookingId);
        }

        @GetMapping("/status/{bookingId}")
        public Map<String, Object> getPaymentStatus(
                        @PathVariable String bookingId) {

                Booking booking = bookingRepository
                                .findByBookingId(bookingId)
                                .orElseThrow(() -> new RuntimeException("Booking not found"));

                return Map.of(
                                "paid",
                                "PAID".equals(booking.getPaymentLinkStatus()));
        }
}