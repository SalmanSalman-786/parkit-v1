package com.parking.backend.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    public void sendSms(String phoneNumber, String message) {

        System.out.println("\n========== SMS ==========");
        System.out.println("TO: " + phoneNumber);
        System.out.println("MESSAGE: " + message);
        System.out.println("=========================\n");
    }
}