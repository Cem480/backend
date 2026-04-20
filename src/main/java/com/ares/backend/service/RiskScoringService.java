package com.ares.backend.service;

import com.ares.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class RiskScoringService {

    // ─────────────────────────────────────────
    // MAIN: Calculate Risk Score (0 to 100)
    // ─────────────────────────────────────────
    public int calculateRiskScore(User user, String incomingIp) {
        int score = 0;
        List<String> triggeredSignals = new java.util.ArrayList<>();

        // ── Signal 1: New IP Address (+20) ───
        if (incomingIp != null && !incomingIp.equals("unknown")) {
            if (user.getKnownIpAddresses() == null ||
                    !user.getKnownIpAddresses().contains(incomingIp)) {
                score += 20;
                triggeredSignals.add("NEW_IP");
                log.info("Risk Signal: NEW_IP +20");
            }
        }

        // ── Signal 2: Failed Attempts ─────────
        int attempts = user.getFailedLoginAttempts();
        if (attempts >= 10) {
            score += 40;
            triggeredSignals.add("BRUTE_FORCE");
            log.info("Risk Signal: BRUTE_FORCE +40");
        } else if (attempts >= 5) {
            score += 25;
            triggeredSignals.add("HIGH_ATTEMPTS");
            log.info("Risk Signal: HIGH_ATTEMPTS +25");
        } else if (attempts >= 1) {
            score += attempts * 5;
            triggeredSignals.add("FAILED_ATTEMPTS_" + attempts);
            log.info("Risk Signal: FAILED_ATTEMPTS +{}", attempts * 5);
        }

        // ── Signal 3: Unusual Hour (+15) ─────
        int currentHour = LocalDateTime.now().getHour();
        if (currentHour >= 1 && currentHour <= 5) {
            score += 15;
            triggeredSignals.add("UNUSUAL_HOUR");
            log.info("Risk Signal: UNUSUAL_HOUR +15");
        }

        // ── Signal 4: Account Never Logged In
        if (user.getLastLoginTime() == null) {
            score += 10;
            triggeredSignals.add("FIRST_LOGIN");
            log.info("Risk Signal: FIRST_LOGIN +10");
        }

        // ── Signal 5: Long Inactivity (+10) ──
        if (user.getLastLoginTime() != null) {
            long daysSinceLogin = java.time.temporal.ChronoUnit.DAYS
                    .between(user.getLastLoginTime(), LocalDateTime.now());
            if (daysSinceLogin > 30) {
                score += 10;
                triggeredSignals.add("LONG_INACTIVITY");
                log.info("Risk Signal: LONG_INACTIVITY +10");
            }
        }

        // Cap at 100
        score = Math.min(score, 100);

        log.info("Final Risk Score: {} | Signals: {}", score, triggeredSignals);
        return score;
    }

    // ─────────────────────────────────────────
    // Decide what to do based on score
    // ─────────────────────────────────────────
    public RiskLevel evaluateRiskLevel(int score) {
        if (score <= 30)
            return RiskLevel.LOW;
        if (score < 70)
            return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    public enum RiskLevel {
        LOW, // → Allow login directly
        MEDIUM, // → Challenge user
        HIGH // → Send to LLM for analysis
    }
}