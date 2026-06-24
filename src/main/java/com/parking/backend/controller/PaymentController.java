package com.parking.backend.controller;

import com.parking.backend.model.Booking;
import com.parking.backend.model.Parking;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.ParkingRepository;
import com.parking.backend.service.PaymentService;

import java.time.LocalDateTime;
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

        public PaymentController(
                        PaymentService paymentService,
                        BookingRepository bookingRepository,
                        ParkingRepository parkingRepository) {

                this.paymentService = paymentService;
                this.bookingRepository = bookingRepository;
                this.parkingRepository = parkingRepository;
        }

        @PostMapping("/create-order") // User App (booking screen m8)
        public String createOrder(@RequestParam double amount) {

                return paymentService.createOrder(amount);
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

        // @GetMapping("/test-refund")
        // public String testRefund(
        //                 @RequestParam String paymentId,
        //                 @RequestParam double amount) {

        //         log.info("Test refund requested. PaymentId={}", paymentId);

        //         return paymentService.refundPayment(
        //                         paymentId,
        //                         amount);
        // }

        @PostMapping("/webhook")
        public String handleWebhook(
                        @RequestBody String payload,
                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

                try {

                        JSONObject json = new JSONObject(payload);

                        String event = json.getString("event");

                        log.info("Webhook received. Event={}", event);

                } catch (Exception e) {

                        e.printStackTrace();
                }

                return "OK";
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
}