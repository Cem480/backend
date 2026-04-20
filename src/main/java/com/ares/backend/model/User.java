package com.ares.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String phone;
    private String password; // hashed with BCrypt
    private String provider; // "LOCAL", "GOOGLE", "FACEBOOK"
    private String providerId; // Google/Facebook user ID

    // ✅ Account State Machine (for your State Diagram!)
    private AccountStatus status; // ACTIVE, LOCKED, CHALLENGED, SUSPENDED

    // Risk Scoring Fields
    private int failedLoginAttempts;
    private List<String> knownIpAddresses;
    private LocalDateTime lastLoginTime;
    private LocalDateTime lockedUntil;

    public enum AccountStatus {
        ACTIVE, LOCKED, CHALLENGED, SUSPENDED
    }
}