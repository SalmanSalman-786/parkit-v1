package com.parking.backend.controller;

import java.util.List;
import java.util.Map;


import org.springframework.web.bind.annotation.*;

import com.parking.backend.model.Booking;
import com.parking.backend.service.GuardService;

@RestController
@RequestMapping("/api/guard")
@CrossOrigin("*")
public class GuardController {

        private final GuardService guardService;

        GuardController(GuardService guardService) {
                this.guardService = guardService;
        }

        @GetMapping("/dashboard/{parkingId}")        // Guard App (monitoring screen m3)
        public Map<String, Object> getDashboard(
                        @PathVariable String parkingId) {

                return guardService.getDashboard(parkingId);
        }

        @GetMapping("/capacity/{parkingId}")
        public Map<String, Object> getCapacity(
                        @PathVariable String parkingId) {

                return guardService.getCapacity(
                                parkingId);
        }

        @GetMapping("/upcoming/{parkingId}")   // Guard App (monitoring screen m5)
        public List<Map<String, Object>> getUpcomingArrivals(
                        @PathVariable String parkingId) {

                return guardService.getUpcomingArrivals(parkingId);
        }

        @GetMapping("/logs/{parkingId}")
        public List<Booking> getLogs(
                        @PathVariable String parkingId) {

                return guardService
                                .getTodayLogs(parkingId);
        }

        @GetMapping("/revenue/{parkingId}")    // Guard App (monitoring screen m2)
        public Map<String, Object> revenue(
                        @PathVariable String parkingId) {

                return guardService
                                .getRevenueStats(parkingId);
        }

        @GetMapping("/activity/{parkingId}")       // Guard App (monitoring screen m6 + today activity m1) 
        public List<Map<String, Object>> activity(
                        @PathVariable String parkingId) {

                return guardService
                                .getTodayActivity(
                                                parkingId);
        }

        @GetMapping("/bookings/{parkingId}")
        public List<Booking> bookings(
                        @PathVariable String parkingId) {

                return guardService
                                .getTodayBookings(
                                                parkingId);
        }

        @GetMapping("/inside")     // Guard App (inside vehicle m1)
        public List<Map<String, Object>> getInsideVehicles(
                        @RequestParam String parkingId,
                        @RequestParam String vehicleType,
                        @RequestParam String sourceType) {

                return guardService.getInsideVehicles(
                                parkingId,
                                vehicleType,
                                sourceType);
        }

        @GetMapping("/revenue-details/{parkingId}")   // Guard App (revenue details m1)
        public Map<String, Object> revenueDetails(
                        @PathVariable String parkingId) {

                return guardService
                                .getRevenueDetails(parkingId);
        }

        @GetMapping("/capacity-breakdown/{parkingId}")     // Guard App (monitoring screen m1)
        public Map<String, Object> capacityBreakdown(
                        @PathVariable String parkingId) {

                return guardService.getCapacityBreakdown(
                                parkingId);
        }

}