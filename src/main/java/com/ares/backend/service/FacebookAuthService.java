package com.ares.backend.service;

import com.ares.backend.dto.AuthResponse;
import com.ares.backend.model.User;
import com.ares.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    private final WebClient webClient = WebClient.create();

    public AuthResponse loginWithFacebook(String accessToken) {

        // 1. Verify token with Facebook & get user info
        Map<String, Object> fbUser = getFacebookUserInfo(accessToken);

        if (fbUser == null) {
            return new AuthResponse(null, "ERROR", "Invalid Facebook token", null);
        }

        String fbId = (String) fbUser.get("id");
        String email = (String) fbUser.get("email");
        String name = (String) fbUser.get("name");

        log.info("Facebook login attempt for: {}", email);

        // 2. Check if user already exists
        Optional<User> existingUser = userRepository
                .findByProviderAndProviderId("FACEBOOK", fbId);

        if (existingUser.isPresent()) {
            // Already registered → just login
            User user = existingUser.get();
            String token = jwtService.generateToken(user.getId(), user.getEmail());
            return new AuthResponse(token, "SUCCESS", "Facebook login successful", user.getId());
        }

        // 3. New user → register them
        User newUser = new User();
        newUser.setEmail(email != null ? email : fbId + "@facebook.com");
        newUser.setProvider("FACEBOOK");
        newUser.setProviderId(fbId);
        newUser.setStatus(User.AccountStatus.ACTIVE);
        newUser.setFailedLoginAttempts(0);
        newUser.setKnownIpAddresses(new ArrayList<>());

        User saved = userRepository.save(newUser);
        log.info("New Facebook user registered: {}", saved.getEmail());

        String token = jwtService.generateToken(saved.getId(), saved.getEmail());
        return new AuthResponse(token, "SUCCESS", "Facebook registration successful", saved.getId());
    }

    // ── Call Facebook Graph API to verify token ──
    private Map<String, Object> getFacebookUserInfo(String accessToken) {
        try {
            return webClient.get()
                    .uri("https://graph.facebook.com/me?fields=id,name,email&access_token=" + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to verify Facebook token: {}", e.getMessage());
            return null;
        }
    }
}