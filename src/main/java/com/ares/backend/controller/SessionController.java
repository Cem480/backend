package com.ares.backend.controller;

import com.ares.backend.dto.AnswerRequest;
import com.ares.backend.model.UserSession;
import com.ares.backend.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:8081", "http://localhost:19006" })
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<UserSession> startSession(
            @RequestParam String surveyId,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(sessionService.startSession(surveyId, userId));
    }

    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<UserSession> saveAnswer(
            @PathVariable String sessionId,
            @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(sessionService.saveAnswer(sessionId, request));
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<UserSession> submitSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.submitSession(sessionId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<UserSession> getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}