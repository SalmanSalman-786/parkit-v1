package com.parking.backend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.parking.backend.model.WaitlistRequest;
import com.parking.backend.service.WaitlistService;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(
            WaitlistService waitlistService) {

        this.waitlistService = waitlistService;
    }

    @PostMapping
    public WaitlistRequest joinWaitlist(
            @RequestBody WaitlistRequest request,
            Authentication auth) {

        request.setUserId(
                auth.getName());

        return waitlistService
                .joinWaitlist(request);
    }
}