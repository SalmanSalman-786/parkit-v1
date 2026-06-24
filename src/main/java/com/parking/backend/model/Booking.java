package com.parking.backend.model;

import lombok.Data;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
    name = "bookings",
    indexes = {
        @Index(name = "idx_booking_user", columnList = "userId"),
        @Index(name = "idx_booking_status", columnList = "status"),
        @Index(name = "idx_booking_vehicle", columnList = "vehicleNumber"),
        @Index(name = "idx_booking_parking", columnList = "parkingId"),
        @Index(name = "idx_booking_vehicle_type", columnList = "vehicleType"),
        @Index(name = "idx_booking_start_time", columnList = "startTime"),
        @Index(name = "idx_booking_end_time", columnList = "endTime")
    }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @Column(unique = true)
    private String bookingId;

    private String vehicleNumber;

    private String phoneNumber;

    private String parkingId;

    private String parkingName;

    private String location;

    private String parkingImageUrl;

    private String vehicleType;

    private String type;

    private String status;

    private boolean isCancelled;

    private String cancelReason;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime entryTime;

    private LocalDateTime exitTime;

    private LocalDateTime expectedExitTime;

    private LocalDateTime createdAt;

    private long durationMinutes;

    private double bookingFee;

    private double assuranceDeposit;

    private double amount;

    private String paymentStatus;

    private String paymentMode;

    private String razorpayOrderId;

    private String razorpayPaymentId;

    private LocalDateTime paymentTime;

    private double refundAmount;

    private String refundId;

    private String refundStatus;

    private LocalDateTime refundTime;

    private boolean depositRefunded;

    private LocalDateTime depositRefundTime;

    private String depositRefundId;

    private String depositRefundStatus;

    private double fineAmount;

    private double collectedFineAmount;

    private boolean finePaid = false;

    private LocalDateTime lastFineTime;

    private String finePaymentMode;

    private String fineOrderId;

    private String finePaymentId;

    private LocalDateTime finePaymentTime;

    private boolean reminderSent = false;

    private boolean startNotificationSent;

    private boolean startTimeAlertSent = false;

    private boolean lateEntryWarningSent = false;

    private boolean expiryAlertSent = false;

    private boolean endTimeNotified;
}