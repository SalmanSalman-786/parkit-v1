// package com.parking.backend.model;

// import java.time.LocalDate;
// import java.time.LocalDateTime;

// import org.springframework.data.annotation.Id;
// import org.springframework.data.mongodb.core.mapping.Document;
// import org.springframework.data.mongodb.core.index.Indexed;

// import lombok.Data;

// @Data
// @Document(collection = "waitlist_requests")
// public class WaitlistRequest {

//     @Id
//     private String id;

//     @Indexed
//     private String userId;

//     @Indexed
//     private String parkingId;

//     @Indexed
//     private String vehicleType;

//     @Indexed
//     private LocalDate bookingDate;

//     private int queuePosition;

//     private boolean notified;

//     private LocalDateTime notificationTime;

//     private int missedNotifications;

//     private LocalDateTime createdAt;

//     private boolean booked;
// }

package com.parking.backend.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "waitlist_requests")
public class WaitlistRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    private String parkingId;

    private String vehicleType;

    private LocalDate bookingDate;

    private int queuePosition;

    private boolean notified;

    private LocalDateTime notificationTime;

    private int missedNotifications;

    private LocalDateTime createdAt;

    private boolean booked;
}