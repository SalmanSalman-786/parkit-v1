package com.parking.backend.model;

public enum AuditAction {

    // ==========================
    // Authentication
    // ==========================

    USER_LOGIN,
    GUARD_LOGIN,
    ADMIN_LOGIN,
    FAILED_LOGIN,
    LOGOUT,

    // ==========================
    // Booking
    // ==========================

    BOOKING_CREATED,
    BOOKING_CANCELLED,
    BOOKING_EXPIRED,
    ENTRY_MARKED,
    EXIT_MARKED,
    WALKIN_ENTRY,

    // ==========================
    // Payment
    // ==========================

    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    REFUND,
    FINE_PAYMENT,

    // ==========================
    // Parking
    // ==========================

    PARKING_ADDED,
    PARKING_UPDATED,
    PARKING_DELETED,

    // ==========================
    // Guard
    // ==========================

    GUARD_ADDED,
    GUARD_REMOVED,

    // ==========================
    // Tariff
    // ==========================

    TARIFF_CHANGED

}