package com.parking.backend.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.parking.backend.dto.WebhookPaymentDto;
import com.parking.backend.dto.WebhookRefundDto;
import com.parking.backend.dto.WebhookResult;
import com.parking.backend.model.AuditAction;
import com.parking.backend.model.AuditActorRole;
import com.parking.backend.model.Booking;
import com.parking.backend.model.User;
import com.parking.backend.repository.BookingRepository;
import com.parking.backend.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WebhookService {
        private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

        @Value("${razorpay.webhook.secret}")
        private String webhookSecret;

        private final SimpMessagingTemplate messagingTemplate;

        private final BookingService bookingService;
        private final BookingRepository bookingRepository;
        private final UserRepository userRepository;
        private final PaymentService paymentService;
        private final AuditLogService auditLogService;

        public WebhookService(
                        BookingService bookingService,
                        BookingRepository bookingRepository,
                        PaymentService paymentService,
                        SimpMessagingTemplate messagingTemplate,
                        UserRepository userRepository,
                        AuditLogService auditLogService) {

                this.bookingService = bookingService;
                this.bookingRepository = bookingRepository;
                this.paymentService = paymentService;
                this.messagingTemplate = messagingTemplate;
                this.userRepository = userRepository;
                this.auditLogService = auditLogService;
        }

        public boolean verifyWebhookSignature(
                        String payload,
                        String razorpaySignature) {

                try {

                        Mac sha256Hmac = Mac.getInstance("HmacSHA256");

                        SecretKeySpec secretKey = new SecretKeySpec(
                                        webhookSecret.getBytes(StandardCharsets.UTF_8),
                                        "HmacSHA256");

                        sha256Hmac.init(secretKey);

                        byte[] hash = sha256Hmac.doFinal(
                                        payload.getBytes(StandardCharsets.UTF_8));

                        StringBuilder hex = new StringBuilder();

                        for (byte b : hash) {
                                hex.append(String.format("%02x", b));
                        }

                        return java.security.MessageDigest.isEqual(
                                        hex.toString().getBytes(StandardCharsets.UTF_8),
                                        razorpaySignature.getBytes(StandardCharsets.UTF_8));

                } catch (Exception e) {

                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Webhook signature verification failed");
                }
        }

        public WebhookResult processWebhook(String payload) {

                JSONObject json = new JSONObject(payload);

                String event = json.getString("event");
                log.debug("Webhook received. Event={}", event);

                switch (event) {

                        case "payment.captured":

                                return handlePaymentCaptured(
                                                extractPayment(payload));

                        case "payment.failed":

                                return handlePaymentFailed(
                                                extractPayment(payload));

                        case "refund.processed":

                                return handleRefundProcessed(
                                                extractRefund(payload));

                        case "refund.failed":

                                return handleRefundFailed(
                                                extractRefund(payload));

                        case "order.paid":

                                log.info("Received order.paid webhook");

                                return new WebhookResult(
                                                true,
                                                false,
                                                "Order paid");

                        default:

                                log.warn("Unknown webhook event: {}", event);

                                return new WebhookResult(
                                                true,
                                                false,
                                                "Unknown event");
                }
        }

        private WebhookPaymentDto extractPayment(String payload) {

                JSONObject json = new JSONObject(payload);

                WebhookPaymentDto dto = new WebhookPaymentDto();

                dto.setEvent(
                                json.getString("event"));

                JSONObject entity = json
                                .getJSONObject("payload")
                                .getJSONObject("payment")
                                .getJSONObject("entity");

                dto.setPaymentId(
                                entity.getString("id"));

                dto.setPaymentDescription(
                                entity.optString("description", ""));

                dto.setOrderId(
                                entity.getString("order_id"));

                dto.setStatus(
                                entity.getString("status"));

                dto.setAmount(
                                entity.getDouble("amount") / 100.0);

                Object notesObj = entity.opt("notes");

                if (notesObj instanceof JSONObject notes) {

                        if (notes.has("bookingId")) {
                                dto.setReceipt(notes.getString("bookingId"));
                        }
                }
                return dto;
        }

        private WebhookRefundDto extractRefund(String payload) {

                JSONObject json = new JSONObject(payload);

                WebhookRefundDto dto = new WebhookRefundDto();

                dto.setEvent(json.getString("event"));

                JSONObject entity = json
                                .getJSONObject("payload")
                                .getJSONObject("refund")
                                .getJSONObject("entity");

                dto.setRefundId(
                                entity.getString("id"));

                dto.setPaymentId(
                                entity.getString("payment_id"));

                dto.setStatus(
                                entity.getString("status"));

                dto.setAmount(
                                entity.getDouble("amount") / 100.0);

                return dto;
        }

        private WebhookResult handlePaymentCaptured(
                        WebhookPaymentDto dto) {

                Booking booking = bookingRepository
                                .findByRazorpayOrderId(dto.getOrderId())
                                .orElse(null);

                log.info("Booking after payment link lookup: {}", booking);

                // Not a booking payment? Try exit/fine payment.
                if (booking == null) {

                        String description = dto.getPaymentDescription();

                        if (description.startsWith("#")) {

                                log.info("Description from webhook: '{}'", dto.getPaymentDescription());

                                String paymentLinkId = "plink_" + dto.getPaymentDescription().substring(1);

                                log.info("Constructed Payment Link ID: '{}'", paymentLinkId);

                                log.info("Looking up Payment Link {}", paymentLinkId);

                                booking = bookingRepository
                                                .findByPaymentLinkId(paymentLinkId)
                                                .orElse(null);

                                log.info("Repository returned: {}", booking);
                        }
                }

                if (booking == null) {

                        log.warn(
                                        "Booking not found for any Order ID {}",
                                        dto.getOrderId());

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Booking not found");
                }

                // ----------------------------
                // Booking payment
                // ----------------------------
                if (dto.getOrderId().equals(booking.getRazorpayOrderId())) {

                        if ("BOOKED".equals(booking.getStatus())) {

                                return new WebhookResult(
                                                true,
                                                false,
                                                "Duplicate booking webhook");
                        }

                        bookingService.confirmPayment(
                                        booking.getBookingId(),
                                        dto.getPaymentId(),
                                        dto.getOrderId());

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Booking payment processed");
                }

                // ----------------------------
                // Exit / Fine payment
                // ----------------------------
                // Exit/Fine payment (Payment Link)

                if (booking.getPaymentLinkId() != null) {

                        if (booking.getRazorpayPaymentId() != null &&
                                        booking.getRazorpayPaymentId().equals(dto.getPaymentId())) {

                                log.info("Duplicate exit webhook ignored for booking {}",
                                                booking.getBookingId());

                                return new WebhookResult(
                                                true,
                                                false,
                                                "Duplicate exit webhook");
                        }

                        log.info("Calling processExitPayment for booking {}",
                                        booking.getBookingId());

                        processExitPayment(
                                        booking,
                                        dto);

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Exit/Fine payment processed");
                }

                return new WebhookResult(
                                true,
                                false,
                                "Unhandled payment");
        }

        private WebhookResult handlePaymentFailed(
                        WebhookPaymentDto dto) {

                Booking booking = bookingRepository
                                .findByRazorpayOrderId(dto.getOrderId())
                                .orElse(null);

                if (booking == null) {

                        log.warn(
                                        "Booking not found for Razorpay Order {}",
                                        dto.getOrderId());

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Booking not found");
                }

                if (!"PENDING_PAYMENT".equals(booking.getStatus())) {

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Payment already handled");
                }

                booking.setStatus("FAILED");
                booking.setPaymentStatus("FAILED");

                bookingRepository.save(booking);

                User user = userRepository.findById(booking.getUserId())
                                .orElse(null);

                auditLogService.log(
                                booking.getUserId(),
                                user != null ? user.getUsername() : null,
                                user != null ? user.getName() : null,
                                AuditActorRole.USER,
                                AuditAction.PAYMENT_FAILED,
                                "BOOKING",
                                booking.getBookingId(),
                                "Online payment failed",
                                "RAZORPAY_WEBHOOK",
                                false);

                log.info(
                                "Payment failed for booking {}",
                                booking.getBookingId());

                return new WebhookResult(
                                true,
                                false,
                                "Payment failed");
        }

        private WebhookResult handleRefundProcessed(
                        WebhookRefundDto dto) {

                Booking booking = bookingRepository
                                .findByRazorpayPaymentId(dto.getPaymentId())
                                .orElse(null);

                if (booking == null) {

                        log.warn(
                                        "Booking not found for Razorpay Payment {}",
                                        dto.getPaymentId());

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Booking not found");
                }

                // Already processed
                if ("SUCCESS".equals(booking.getRefundStatus())
                                && "SUCCESS".equals(booking.getDepositRefundStatus())) {

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Duplicate refund webhook");
                }

                // Booking cancellation refund
                if (booking.getRefundId() != null) {

                        booking.setRefundId(dto.getRefundId());

                        booking.setRefundStatus("SUCCESS");

                        booking.setRefundTime(LocalDateTime.now());
                }

                // Deposit refund
                if (booking.isDepositRefunded()) {

                        booking.setDepositRefundId(dto.getRefundId());

                        booking.setDepositRefundStatus("SUCCESS");

                        booking.setDepositRefundTime(LocalDateTime.now());
                }

                bookingRepository.save(booking);

                User user = userRepository.findById(booking.getUserId())
                                .orElse(null);

                auditLogService.log(
                                booking.getUserId(),
                                user != null ? user.getUsername() : null,
                                user != null ? user.getName() : null,
                                AuditActorRole.USER,
                                AuditAction.REFUND,
                                "BOOKING",
                                booking.getBookingId(),
                                "Refund processed. Refund ID: " + dto.getRefundId(),
                                "RAZORPAY_WEBHOOK",
                                true);

                log.info(
                                "Refund processed for booking {}",
                                booking.getBookingId());

                return new WebhookResult(
                                true,
                                false,
                                "Refund processed");
        }

        private WebhookResult handleRefundFailed(
                        WebhookRefundDto dto) {

                Booking booking = bookingRepository
                                .findByRazorpayPaymentId(dto.getPaymentId())
                                .orElse(null);

                if (booking == null) {

                        log.warn(
                                        "Booking not found for Razorpay Payment {}",
                                        dto.getPaymentId());

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Booking not found");
                }

                if ("FAILED".equals(booking.getRefundStatus())
                                && "FAILED".equals(booking.getDepositRefundStatus())) {

                        return new WebhookResult(
                                        true,
                                        false,
                                        "Duplicate refund failure webhook");
                }

                // Booking cancellation refund
                if (booking.getRefundId() != null) {

                        booking.setRefundStatus("FAILED");

                        booking.setRefundTime(LocalDateTime.now());
                }

                // Deposit refund
                if (booking.isDepositRefunded()) {

                        booking.setDepositRefundStatus("FAILED");

                        booking.setDepositRefundTime(LocalDateTime.now());
                }

                bookingRepository.save(booking);

                log.error(
                                "Refund failed for booking {}",
                                booking.getBookingId());

                return new WebhookResult(
                                true,
                                false,
                                "Refund failed");
        }

        private void processExitPayment(
                        Booking booking,
                        WebhookPaymentDto dto) {

                log.info(">>> ENTER processExitPayment");

                if ("PAID".equals(booking.getPaymentLinkStatus())) {

                        log.info("Duplicate payment link webhook ignored");

                        return;
                }

                log.info("Calling completeExitPayment...");

                paymentService.completeExitPayment(
                                booking,
                                dto.getPaymentId());

                if (booking.getFineAmount() > 0) {

                        User user = userRepository.findById(booking.getUserId())
                                        .orElse(null);

                        auditLogService.log(
                                        booking.getUserId(),
                                        user != null ? user.getUsername() : null,
                                        user != null ? user.getName() : null,
                                        AuditActorRole.USER,
                                        AuditAction.FINE_PAYMENT,
                                        "BOOKING",
                                        booking.getBookingId(),
                                        "Fine payment successful. Amount: ₹" + booking.getFineAmount(),
                                        "RAZORPAY_WEBHOOK",
                                        true);
                }

                log.info("completeExitPayment finished");

                booking.setPaymentLinkStatus("PAID");

                bookingRepository.save(booking);

                log.info("Sending websocket event...");

                messagingTemplate.convertAndSend(
                                "/topic/payment/" + booking.getBookingId(),
                                "PAYMENT_SUCCESS");

                log.info("Exit/Fine payment processed for booking {}",
                                booking.getBookingId());

                log.info("Payment success event sent for {}",
                                booking.getBookingId());
        }
}