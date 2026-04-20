package com.ares.backend.dto;

import lombok.Data;

@Data
public class LoginRequest {

    // User can login with email OR phone
    private String email;
    private String phone;

    private String password;

    // The IP address sent from frontend (for risk scoring later)
    private String ipAddress;
}