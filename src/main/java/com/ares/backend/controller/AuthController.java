package com.ares.backend.controller;

import com.ares.backend.dto.AuthResponse;
import com.ares.backend.dto.LoginRequest;
import com.ares.backend.dto.RegisterRequest;
import com.ares.backend.service.AuthService;
import com.ares.backend.service.FacebookAuthService;
import com.ares.backend.service.GoogleAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    private final AuthService authService;
    private final FacebookAuthService facebookAuthService;
    private final GoogleAuthService googleAuthService;

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // POST /api/auth/google
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(
            @RequestBody Map<String, String> body) {
        String token = body.get("token");
        return ResponseEntity.ok(googleAuthService.loginWithGoogle(token));
    }

    // POST /api/auth/facebook
    @PostMapping("/facebook")
    public ResponseEntity<AuthResponse> facebookLogin(
            @RequestBody Map<String, String> body) {
        String accessToken = body.get("accessToken");
        return ResponseEntity.ok(facebookAuthService.loginWithFacebook(accessToken));
    }

    // GET /api/auth/health
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Ares Backend is running! ✅");
    }
}