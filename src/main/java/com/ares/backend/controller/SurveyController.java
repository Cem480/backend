package com.ares.backend.controller;

import com.ares.backend.dto.SurveyRequest;
import com.ares.backend.model.Survey;
import com.ares.backend.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/surveys")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    public ResponseEntity<Survey> createSurvey(
            @RequestBody SurveyRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(surveyService.createSurvey(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<Survey>> getAllSurveys() {
        return ResponseEntity.ok(surveyService.getAllSurveys());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Survey> getSurvey(@PathVariable String id) {
        return surveyService.getSurveyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Survey> updateSurvey(
            @PathVariable String id,
            @RequestBody SurveyRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(surveyService.updateSurvey(id, request, userId));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<Survey> publishSurvey(@PathVariable String id) {
        return ResponseEntity.ok(surveyService.publishSurvey(id));
    }
}