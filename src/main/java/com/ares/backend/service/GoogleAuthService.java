package com.ares.backend.service;

import com.ares.backend.dto.AuthResponse;
import com.ares.backend.model.User;
import com.ares.backend.repository.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    public AuthResponse loginWithGoogle(String idToken) {
        try {
            // 1. Verify the Google token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken googleIdToken = verifier.verify(idToken);

            if (googleIdToken == null) {
                return new AuthResponse(null, "ERROR", "Invalid Google token", null);
            }

            // 2. Extract user info from token
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            log.info("Google login attempt for: {}", email);

            // 3. Check if user already exists
            Optional<User> existingUser = userRepository
                    .findByProviderAndProviderId("GOOGLE", googleId);

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                String token = jwtService.generateToken(user.getId(), user.getEmail());
                log.info("Existing Google user logged in: {}", email);
                return new AuthResponse(token, "SUCCESS", "Google login successful", user.getId());
            }

            // 4. New user → register
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setProvider("GOOGLE");
            newUser.setProviderId(googleId);
            newUser.setStatus(User.AccountStatus.ACTIVE);
            newUser.setFailedLoginAttempts(0);
            newUser.setKnownIpAddresses(new ArrayList<>());

            User saved = userRepository.save(newUser);
            log.info("New Google user registered: {}", email);

            String token = jwtService.generateToken(saved.getId(), saved.getEmail());
            return new AuthResponse(token, "SUCCESS", "Google registration successful", saved.getId());

        } catch (Exception e) {
            log.error("Google auth error: {}", e.getMessage());
            return new AuthResponse(null, "ERROR", "Google authentication failed", null);
        }
    }
}