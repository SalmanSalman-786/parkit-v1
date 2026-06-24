package com.parking.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserLoginRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^[0-9]{10}$",
        message = "Invalid phone number"
    )
    private String phoneNumber;

    @Size(max = 50)
    private String name;

    @NotBlank(message = "Firebase token is required")
    private String firebaseToken;
}