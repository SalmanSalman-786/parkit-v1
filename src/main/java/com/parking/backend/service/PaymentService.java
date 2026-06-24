package com.parking.backend.service;

import com.parking.backend.model.Booking;
import com.parking.backend.repository.BookingRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import com.razorpay.Utils;
import com.razorpay.Refund;

@Service
public class PaymentService {

        private final BookingRepository bookingRepository;

        // 🔥 TEST KEYS
        @Value("${razorpay.key.id}")
        private String keyId;

        @Value("${razorpay.key.secret}")
        private String keySecret;

        PaymentService(BookingRepository bookingRepository) {
                this.bookingRepository = bookingRepository;
        }

        public String createOrder(double amount) { // User App (booking screen m8) + Guard App (operations screen m6)

                try {

                        RazorpayClient client = new RazorpayClient(keyId, keySecret);
                        ;

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

                        e.printStackTrace(); // 🔥 ADD THIS

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
                booking.setPaymentStatus("PAID");

                booking.setRazorpayPaymentId(
                                paymentId);

                booking.setPaymentTime(
                                LocalDateTime.now());

                // Fine tracking only if fine exists
                if (booking.getFineAmount() > 0) {

                        booking.setFinePaid(true);

                        booking.setFinePaymentMode("ONLINE");

                        booking.setFinePaymentId(
                                        paymentId);

                        booking.setFinePaymentTime(
                                        LocalDateTime.now());

                        booking.setCollectedFineAmount(
                                        booking.getFineAmount());
                }
                booking.setPaymentMode("ONLINE");

                bookingRepository.save(booking);

                return true;
        }
}