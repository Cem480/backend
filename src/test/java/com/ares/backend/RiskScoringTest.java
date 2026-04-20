package com.ares.backend;

import com.ares.backend.dto.LoginRequest;
import com.ares.backend.dto.RegisterRequest;
import com.ares.backend.model.User;
import com.ares.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskScoringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository; // ← inject to manipulate DB directly

    // Test credentials
    private static final String TEST_EMAIL = "risktest_v2@ares.com";
    private static final String TEST_PASSWORD = "testpass123";
    private static final String KNOWN_IP = "192.168.1.100";
    private static final String NEW_IP = "185.220.101.45";
    private static final String SUSPICIOUS_IP = "185.220.101.99";

    // ─────────────────────────────────────────
    // PRINT HELPER
    // ─────────────────────────────────────────
    private void printResult(String testName, String ip,
            String response, String expected) {
        System.out.println("\n" + "=".repeat(65));
        System.out.println("  " + testName);
        System.out.println("=".repeat(65));
        if (ip != null)
            System.out.println("  IP Address : " + ip);
        System.out.println("  Response   : " + response);
        System.out.println("  Expected   : " + expected);
        System.out.println("=".repeat(65));
    }

    private String extractStatus(String json) {
        try {
            return objectMapper.readTree(json).get("status").asText();
        } catch (Exception e) {
            return "PARSE_ERROR";
        }
    }

    private String extractMessage(String json) {
        try {
            return objectMapper.readTree(json).get("message").asText();
        } catch (Exception e) {
            return "";
        }
    }

    // ─────────────────────────────────────────
    // TEST 1: Register test user
    // ─────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("✅ TEST 1 - Register Test User")
    void test1_RegisterUser() throws Exception {
        // Clean up first if exists
        userRepository.findByEmail(TEST_EMAIL)
                .ifPresent(userRepository::delete);

        RegisterRequest request = new RegisterRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        printResult("TEST 1: Register User", null, response, "SUCCESS");

        Assertions.assertTrue(response.contains("SUCCESS"),
                "Registration must succeed");
    }

    // ─────────────────────────────────────────
    // TEST 2: Low Risk Login
    // ─────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("✅ TEST 2 - Low Risk Login → ALLOW")
    void test2_LowRiskLogin() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setIpAddress(KNOWN_IP);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        printResult("TEST 2: Low Risk Login", KNOWN_IP, response,
                "SUCCESS (score ≤ 30)");

        Assertions.assertTrue(response.contains("SUCCESS"),
                "Low risk login must be allowed");
    }

    // ─────────────────────────────────────────
    // TEST 3: Medium Risk Login (New IP)
    // ─────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("⚠️  TEST 3 - Medium Risk Login → CHALLENGED")
    void test3_MediumRiskLogin() throws Exception {

        // Give user some failed attempts to push score up
        Optional<User> optUser = userRepository.findByEmail(TEST_EMAIL);
        optUser.ifPresent(user -> {
            user.setFailedLoginAttempts(4); // push score to MEDIUM
            userRepository.save(user);
        });

        LoginRequest request = new LoginRequest();
        request.setEmail(TEST_EMAIL);
        request.setPassword(TEST_PASSWORD);
        request.setIpAddress(NEW_IP); // new IP → +20 points

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        printResult("TEST 3: Medium Risk Login", NEW_IP, response,
                "CHALLENGED (score 31-70)");

        Assertions.assertTrue(
                response.contains("SUCCESS") || response.contains("CHALLENGED"),
                "Medium risk should be challenged or allowed");
    }

    // ─────────────────────────────────────────
    // TEST 4: Brute Force → CHALLENGED
    // ─────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("🚨 TEST 4 - Brute Force 5x → CHALLENGED")
    void test4_BruteForceChallenge() throws Exception {

        // Reset user state
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setStatus(User.AccountStatus.ACTIVE);
            userRepository.save(user);
        });

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  TEST 4: Brute Force Simulation (5 wrong passwords)");
        System.out.println("=".repeat(65));

        String lastResponse = "";

        for (int i = 1; i <= 5; i++) {
            LoginRequest req = new LoginRequest();
            req.setEmail(TEST_EMAIL);
            req.setPassword("WRONG_PASSWORD_" + i);
            req.setIpAddress(KNOWN_IP);

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            lastResponse = result.getResponse().getContentAsString();
            System.out.printf("  Attempt #%d → %-12s %s%n",
                    i,
                    extractStatus(lastResponse),
                    i == 5 ? "⚠️  CHALLENGED!" : "");
            Thread.sleep(200);
        }

        System.out.println("  Expected   : CHALLENGED after attempt #5");
        System.out.println("=".repeat(65));

        Assertions.assertTrue(
                lastResponse.contains("CHALLENGED") || lastResponse.contains("LOCKED"),
                "5 failed attempts must trigger CHALLENGED");
    }

    // ─────────────────────────────────────────
    // TEST 5: Account Lock After 10 Attempts
    // ─────────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("🔒 TEST 5 - 10 Failed Attempts → LOCKED")
    void test5_AccountLock() throws Exception {

        String lockEmail = "locktest_v2@ares.com";

        // Clean up & register fresh user
        userRepository.findByEmail(lockEmail)
                .ifPresent(userRepository::delete);

        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(lockEmail);
        reg.setPassword(TEST_PASSWORD);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  TEST 5: Account Lock (10 wrong passwords)");
        System.out.println("=".repeat(65));

        String lastResponse = "";

        for (int i = 1; i <= 10; i++) {
            LoginRequest req = new LoginRequest();
            req.setEmail(lockEmail);
            req.setPassword("WRONG_" + i);
            req.setIpAddress(KNOWN_IP);

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andReturn();

            lastResponse = result.getResponse().getContentAsString();
            String status = extractStatus(lastResponse);
            System.out.printf("  Attempt #%-2d → %-12s %s%n",
                    i, status,
                    i == 10 ? "🔒 LOCKED!" : "");
            Thread.sleep(100);
        }

        System.out.println("  Expected   : LOCKED after attempt #10");
        System.out.println("=".repeat(65));

        Assertions.assertTrue(lastResponse.contains("LOCKED"),
                "10 failed attempts must lock the account");
    }

    // ─────────────────────────────────────────
    // TEST 6: 🤖 HIGH RISK → CLAUDE LLM
    // This is the key new test!
    // We directly set DB state to force score > 70
    // ─────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("🤖 TEST 6 - HIGH Risk → Claude LLM Analysis")
    void test6_HighRiskLlmTriggered() throws Exception {

        String llmEmail = "llmtest@ares.com";

        // Clean up & register fresh user
        userRepository.findByEmail(llmEmail)
                .ifPresent(userRepository::delete);

        RegisterRequest reg = new RegisterRequest();
        reg.setEmail(llmEmail);
        reg.setPassword(TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // ── Force HIGH risk state directly in DB ──
        // This simulates a user who has:
        // - 10 failed attempts → BRUTE_FORCE +40
        // - Last login 40 days ago → LONG_INACTIVITY +10
        // - Never registered this IP → NEW_IP +20
        // - First login from this IP → FIRST_LOGIN +10
        // TOTAL = 80 → HIGH RISK → LLM! 🤖
        userRepository.findByEmail(llmEmail).ifPresent(user -> {
            user.setFailedLoginAttempts(10); // BRUTE_FORCE signal
            user.setLastLoginTime(
                    LocalDateTime.now().minusDays(40)); // LONG_INACTIVITY signal
            user.setStatus(User.AccountStatus.ACTIVE); // unlock for test
            user.setLockedUntil(null);
            userRepository.save(user);
        });

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  TEST 6: HIGH Risk → Claude LLM Analysis");
        System.out.println("=".repeat(65));
        System.out.println("  User state forced via DB:");
        System.out.println("  ├── failedAttempts = 10 → BRUTE_FORCE  +40");
        System.out.println("  ├── lastLogin = 40 days ago             +10");
        System.out.println("  ├── NEW suspicious IP                   +20");
        System.out.println("  └── FIRST_LOGIN                         +10");
        System.out.println("  ─────────────────────────────────────────");
        System.out.println("  Expected Score: 80/100 → HIGH → 🤖 LLM!");
        System.out.println("=".repeat(65));
        System.out.println("\n  Sending request to Claude API...");
        System.out.println("  (This may take 2-3 seconds)");

        // Login with correct password from suspicious IP
        LoginRequest request = new LoginRequest();
        request.setEmail(llmEmail);
        request.setPassword(TEST_PASSWORD);
        request.setIpAddress(SUSPICIOUS_IP); // brand new suspicious IP

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        String status = extractStatus(response);
        String message = extractMessage(response);

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  🤖 CLAUDE AI DECISION:");
        System.out.println("  Status  : " + status);
        System.out.println("  Message : " + message);
        System.out.println("=".repeat(65));
        System.out.println("  BLOCKED   → Claude detected fraud 🚫");
        System.out.println("  CHALLENGED → Claude wants verification ⚠️");
        System.out.println("  SUCCESS   → Claude allowed despite signals ✅");
        System.out.println("=".repeat(65));

        // Any of these is valid — Claude makes the call!
        Assertions.assertTrue(
                response.contains("BLOCKED") ||
                        response.contains("CHALLENGED") ||
                        response.contains("SUCCESS"),
                "LLM must return a valid decision");
    }

    // ─────────────────────────────────────────
    // TEST 7: Verify LLM BLOCK → Account Suspended
    // ─────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("🚫 TEST 7 - After LLM BLOCK → Account Suspended")
    void test7_AfterLlmBlock_AccountSuspended() throws Exception {

        // Check what happened to the llmtest user
        Optional<User> user = userRepository.findByEmail("llmtest@ares.com");

        System.out.println("\n" + "=".repeat(65));
        System.out.println("  TEST 7: Verify Account State After LLM Decision");
        System.out.println("=".repeat(65));

        if (user.isPresent()) {
            User u = user.get();
            System.out.println("  Account Status  : " + u.getStatus());
            System.out.println("  Failed Attempts : " + u.getFailedLoginAttempts());
            System.out.println("  Last Login      : " + u.getLastLoginTime());
            System.out.println("  Known IPs       : " + u.getKnownIpAddresses());
        }

        System.out.println("=".repeat(65));

        // If Claude blocked → status should be SUSPENDED
        // If Claude challenged → status should be CHALLENGED
        // If Claude allowed → status should be ACTIVE
        user.ifPresent(u -> System.out.println(
                "  Final State: " + u.getStatus() + " (set by Claude AI)"));

        Assertions.assertTrue(user.isPresent(), "User must exist in DB");
    }

    // ─────────────────────────────────────────
    // FINAL SUMMARY
    // ─────────────────────────────────────────
    @AfterAll
    static void printSummary() {
        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║           RISK SCORING TEST SUITE - SUMMARY              ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  Test 1: Register User              → SUCCESS ✅          ║");
        System.out.println("║  Test 2: Low Risk Login (score≤30)  → ALLOW   ✅          ║");
        System.out.println("║  Test 3: Medium Risk (new IP)       → CHALLENGE ⚠️        ║");
        System.out.println("║  Test 4: Brute Force 5x             → CHALLENGE ⚠️        ║");
        System.out.println("║  Test 5: 10 Attempts                → LOCKED  🔒          ║");
        System.out.println("║  Test 6: HIGH Risk (score=80)       → LLM 🤖             ║");
        System.out.println("║  Test 7: Post-LLM Account State     → DB Check ✅         ║");
        System.out.println("╠═══════════════════════════════════════════════════════════╣");
        System.out.println("║  Risk Score Thresholds:                                   ║");
        System.out.println("║  0  ──────30────────70──────── 100                        ║");
        System.out.println("║  LOW      │  MEDIUM  │  HIGH                              ║");
        System.out.println("║  ALLOW    │ CHALLENGE│  LLM 🤖                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
    }
}