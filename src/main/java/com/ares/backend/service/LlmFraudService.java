package com.ares.backend.service;

import com.ares.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LlmFraudService {

    @Value("${anthropic.api.key}")
    private String apiKey;

    @Value("${anthropic.api.model}")
    private String model;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .codecs(configurer -> configurer
                    .defaultCodecs()
                    .maxInMemorySize(1024 * 1024))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─────────────────────────────────────────
    // MAIN: Ask Claude if login is fraudulent
    // ─────────────────────────────────────────
    public FraudDecision analyzeFraud(User user, String ipAddress, int riskScore) {

        String prompt = buildPrompt(user, ipAddress, riskScore);

        try {
            log.info("Sending fraud analysis request to Claude for user: {}", user.getEmail());

            // Build Claude API request
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", 256,
                    "system", buildSystemPrompt(),
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)));

            // Call Claude API
            String response = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(30));

            // Parse response
            return parseClaudeResponse(response);

        } catch (Exception e) {
            log.error("Claude API error: {}", e.getMessage());
            // If LLM fails → be safe and CHALLENGE the user
            return new FraudDecision("CHALLENGE", 50, "LLM unavailable - defaulting to challenge");
        }
    }

    // ─────────────────────────────────────────
    // Build the System Prompt
    // ─────────────────────────────────────────
    private String buildSystemPrompt() {
        return """
                You are a fraud detection AI for a secure authentication system.
                Analyze the login attempt data and respond with ONLY a valid JSON object.
                No explanation, no markdown, no extra text - just the JSON.

                Response format:
                {
                  "decision": "ALLOW" or "CHALLENGE" or "BLOCK",
                  "confidence": <number 0-100>,
                  "reason": "<one sentence explanation>"
                }

                Decision rules:
                - ALLOW: Low risk, legitimate user behavior
                - CHALLENGE: Medium risk, needs additional verification
                - BLOCK: High risk, likely fraudulent activity
                """;
    }

    // ─────────────────────────────────────────
    // Build the User Prompt with login data
    // ─────────────────────────────────────────
    private String buildPrompt(User user, String ipAddress, int riskScore) {
        return String.format("""
                Analyze this login attempt:

                User Email: %s
                Account Provider: %s
                Risk Score: %d/100
                Failed Login Attempts: %d
                IP Address: %s
                Known IPs: %s
                Login Time: %s
                Last Login: %s
                Account Status: %s

                Based on this data, should we ALLOW, CHALLENGE, or BLOCK this login?
                """,
                user.getEmail(),
                user.getProvider(),
                riskScore,
                user.getFailedLoginAttempts(),
                ipAddress,
                user.getKnownIpAddresses() != null ? user.getKnownIpAddresses().toString() : "[]",
                LocalDateTime.now(),
                user.getLastLoginTime() != null ? user.getLastLoginTime() : "Never",
                user.getStatus());
    }

    // ─────────────────────────────────────────
    // Parse Claude's JSON Response
    // ─────────────────────────────────────────
    private FraudDecision parseClaudeResponse(String rawResponse) {
        try {
            // Extract content from Claude response
            Map responseMap = objectMapper.readValue(rawResponse, Map.class);
            List contentList = (List) responseMap.get("content");
            Map firstContent = (Map) contentList.get(0);
            String text = (String) firstContent.get("text");

            log.info("Claude response: {}", text);

            // Parse the JSON decision
            Map decisionMap = objectMapper.readValue(text.trim(), Map.class);

            String decision = (String) decisionMap.get("decision");
            int confidence = (Integer) decisionMap.get("confidence");
            String reason = (String) decisionMap.get("reason");

            log.info("Fraud Decision: {} (confidence: {}%) - {}", decision, confidence, reason);
            return new FraudDecision(decision, confidence, reason);

        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", e.getMessage());
            return new FraudDecision("CHALLENGE", 50, "Parse error - defaulting to challenge");
        }
    }

    // ─────────────────────────────────────────
    // Result Object
    // ─────────────────────────────────────────
    public record FraudDecision(String decision, int confidence, String reason) {
    }
}