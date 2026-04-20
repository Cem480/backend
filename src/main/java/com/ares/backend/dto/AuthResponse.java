package com.ares.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {

    private String token; // JWT token
    private String status; // "SUCCESS", "CHALLENGED", "LOCKED"
    private String message;
    private String userId;
}