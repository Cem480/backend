package com.ares.backend.service;

import com.ares.backend.dto.AuthResponse;
import com.ares.backend.dto.LoginRequest;
import com.ares.backend.dto.RegisterRequest;
import com.ares.backend.model.User;
import com.ares.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RiskScoringService riskScoringService;
    private final LlmFraudService llmFraudService;

    // ─────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────
    public AuthResponse register(RegisterRequest request) {

        // 1. Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            return new AuthResponse(null, "ERROR", "Email already registered", null);
        }

        // 2. Build user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider("LOCAL");
        user.setStatus(User.AccountStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setKnownIpAddresses(new ArrayList<>());

        // 3. Save
        User saved = userRepository.save(user);
        log.info("New user registered: {}", saved.getEmail());

        // 4. Generate token
        String token = jwtService.generateToken(saved.getId(), saved.getEmail());
        return new AuthResponse(token, "SUCCESS", "Registration successful", saved.getId());
    }

    // ─────────────────────────────────────────
    // LOGIN (with Risk Scoring + LLM)
    // ─────────────────────────────────────────
    public AuthResponse login(LoginRequest request) {

        // 1. Find user by email or phone
        Optional<User> optUser = findUserByEmailOrPhone(request);
        if (optUser.isEmpty()) {
            return new AuthResponse(null, "ERROR", "User not found", null);
        }

        User user = optUser.get();

        // 2. Check if account is SUSPENDED permanently
        if (user.getStatus() == User.AccountStatus.SUSPENDED) {
            return new AuthResponse(null, "SUSPENDED",
                    "Account suspended due to suspicious activity. Contact support.", user.getId());
        }

        // 3. Check if account is LOCKED (temporary)
        if (user.getStatus() == User.AccountStatus.LOCKED) {
            if (user.getLockedUntil() != null &&
                    LocalDateTime.now().isBefore(user.getLockedUntil())) {
                return new AuthResponse(null, "LOCKED",
                        "Account locked until " + user.getLockedUntil(), user.getId());
            } else {
                // Lock expired → reset
                log.info("Lock expired for user: {}", user.getEmail());
                user.setStatus(User.AccountStatus.ACTIVE);
                user.setFailedLoginAttempts(0);
            }
        }

        // 4. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return handleFailedAttempt(user, request.getIpAddress());
        }

        // ✅ Password is CORRECT → Run Risk Scoring
        // ─────────────────────────────────────────
        int riskScore = riskScoringService.calculateRiskScore(user, request.getIpAddress());
        RiskScoringService.RiskLevel riskLevel = riskScoringService.evaluateRiskLevel(riskScore);

        log.info("User: {} | Risk Score: {} | Level: {}",
                user.getEmail(), riskScore, riskLevel);

        // 5. Act based on risk level
        switch (riskLevel) {

            case HIGH -> {
                // Send to Claude for deep fraud analysis
                log.warn("HIGH risk detected for {}! Sending to LLM...", user.getEmail());
                LlmFraudService.FraudDecision fraud = llmFraudService.analyzeFraud(user, request.getIpAddress(),
                        riskScore);

                log.info("LLM Decision: {} ({}% confidence) - {}",
                        fraud.decision(), fraud.confidence(), fraud.reason());

                return switch (fraud.decision()) {
                    case "BLOCK" -> {
                        // Permanently suspend account
                        user.setStatus(User.AccountStatus.SUSPENDED);
                        userRepository.save(user);
                        log.error("Account SUSPENDED by AI: {}", user.getEmail());
                        yield new AuthResponse(null, "BLOCKED",
                                "🚫 Access denied by AI fraud detection. Reason: " + fraud.reason(),
                                user.getId());
                    }
                    case "CHALLENGE" -> {
                        user.setStatus(User.AccountStatus.CHALLENGED);
                        userRepository.save(user);
                        yield new AuthResponse(null, "CHALLENGED",
                                "⚠️ Verification required. AI Reason: " + fraud.reason(),
                                user.getId());
                    }
                    default -> {
                        // LLM said ALLOW despite high score
                        log.info("LLM overrode HIGH risk → ALLOW for {}", user.getEmail());
                        yield completeLogin(user, request.getIpAddress(), riskScore);
                    }
                };
            }

            case MEDIUM -> {
                // Challenge without LLM
                log.warn("MEDIUM risk for {}. Challenging user.", user.getEmail());
                user.setStatus(User.AccountStatus.CHALLENGED);
                userRepository.save(user);
                return new AuthResponse(null, "CHALLENGED",
                        "⚠️ Suspicious activity detected. Risk score: " + riskScore + "/100",
                        user.getId());
            }

            case LOW -> {
                // Low risk → allow directly
                return completeLogin(user, request.getIpAddress(), riskScore);
            }

            default -> {
                return completeLogin(user, request.getIpAddress(), riskScore);
            }
        }
    }

    // ─────────────────────────────────────────
    // COMPLETE LOGIN (success path)
    // ─────────────────────────────────────────
    private AuthResponse completeLogin(User user, String ipAddress, int riskScore) {
        // Reset failed attempts
        user.setFailedLoginAttempts(0);
        user.setStatus(User.AccountStatus.ACTIVE);
        user.setLastLoginTime(LocalDateTime.now());

        // Track this IP
        if (ipAddress != null && !ipAddress.equals("unknown")) {
            if (user.getKnownIpAddresses() == null) {
                user.setKnownIpAddresses(new ArrayList<>());
            }
            if (!user.getKnownIpAddresses().contains(ipAddress)) {
                user.getKnownIpAddresses().add(ipAddress);
                log.info("New IP {} registered for user {}", ipAddress, user.getEmail());
            }
        }

        userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        log.info("✅ Login successful for {} | Risk Score: {}/100", user.getEmail(), riskScore);

        return new AuthResponse(token, "SUCCESS",
                "Login successful. Risk score: " + riskScore + "/100", user.getId());
    }

    // ─────────────────────────────────────────
    // HANDLE FAILED LOGIN ATTEMPT
    // ─────────────────────────────────────────
    private AuthResponse handleFailedAttempt(User user, String ipAddress) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        log.warn("Failed attempt #{} for user: {}", attempts, user.getEmail());

        if (attempts >= 10) {
            // LOCK for 30 minutes
            user.setStatus(User.AccountStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            log.error("Account LOCKED after 10 attempts: {}", user.getEmail());
            return new AuthResponse(null, "LOCKED",
                    "🔒 Account locked for 30 minutes after 10 failed attempts.", user.getId());

        } else if (attempts >= 5) {
            // CHALLENGE after 5 attempts
            user.setStatus(User.AccountStatus.CHALLENGED);
            userRepository.save(user);
            return new AuthResponse(null, "CHALLENGED",
                    "⚠️ Suspicious activity. Attempt " + attempts + "/10. Verification required.",
                    user.getId());
        }

        userRepository.save(user);
        return new AuthResponse(null, "ERROR",
                "❌ Invalid password. Attempt " + attempts + "/10", null);
    }

    // ─────────────────────────────────────────
    // HELPER: Find User by Email or Phone
    // ─────────────────────────────────────────
    private Optional<User> findUserByEmailOrPhone(LoginRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return userRepository.findByEmail(request.getEmail());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            return userRepository.findByPhone(request.getPhone());
        }
        return Optional.empty();
    }
}