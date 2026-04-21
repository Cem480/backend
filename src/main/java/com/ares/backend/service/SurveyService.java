package com.ares.backend.service;

import com.ares.backend.dto.SurveyRequest;
import com.ares.backend.model.*;
import com.ares.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SchemaVersionRepository schemaVersionRepository;
    private final SessionRepository sessionRepository;
    private final GBCRAlgorithm gbcrAlgorithm;
    private final SimpMessagingTemplate messagingTemplate;

    // ─────────────────────────────────────────
    // CREATE SURVEY
    // ─────────────────────────────────────────
    public Survey createSurvey(SurveyRequest request, String userId) {
        Survey survey = new Survey();
        survey.setTitle(request.getTitle());
        survey.setNodes(request.getNodes());
        survey.setEdges(request.getEdges());
        survey.setVersion(1);
        survey.setStatus(Survey.SurveyStatus.DRAFT);
        survey.setCreatedBy(userId);
        survey.setCreatedAt(LocalDateTime.now());
        survey.setUpdatedAt(LocalDateTime.now());

        Survey saved = surveyRepository.save(survey);
        log.info("Survey created: {}", saved.getId());
        return saved;
    }

    // ─────────────────────────────────────────
    // GET ALL SURVEYS
    // ─────────────────────────────────────────
    public List<Survey> getAllSurveys() {
        return surveyRepository.findAll();
    }

    // ─────────────────────────────────────────
    // GET SURVEY BY ID
    // ─────────────────────────────────────────
    public Optional<Survey> getSurveyById(String id) {
        return surveyRepository.findById(id);
    }

    // ─────────────────────────────────────────
    // PUBLISH SURVEY
    // ─────────────────────────────────────────
    public Survey publishSurvey(String id) {
        Survey survey = surveyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Survey not found"));
        survey.setStatus(Survey.SurveyStatus.LIVE);
        survey.setUpdatedAt(LocalDateTime.now());
        return surveyRepository.save(survey);
    }

    // ─────────────────────────────────────────
    // UPDATE SURVEY — triggers GBCR for all active sessions
    // ─────────────────────────────────────────
    public Survey updateSurvey(String id, SurveyRequest request, String userId) {
        Survey survey = surveyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Survey not found"));

        // Increment version
        survey.setNodes(request.getNodes());
        survey.setEdges(request.getEdges());
        survey.setVersion(survey.getVersion() + 1);
        survey.setUpdatedAt(LocalDateTime.now());
        Survey updated = surveyRepository.save(survey);

        // Save schema version record
        SchemaVersion sv = new SchemaVersion();
        sv.setSurveyId(id);
        sv.setVersion(updated.getVersion());
        sv.setTimestamp(LocalDateTime.now());
        sv.setPublishedBy(userId);
        schemaVersionRepository.save(sv);

        log.info("Survey {} updated to version {}", id, updated.getVersion());

        // Check all active sessions and push conflict notifications
        checkActiveSessions(updated);

        return updated;
    }

    public void deleteSurvey(String id, String userId) {
        Survey survey = surveyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Survey not found: " + id));

        // Delete sessions for all statuses using existing repository methods
        for (UserSession.SessionStatus status : UserSession.SessionStatus.values()) {
            List<UserSession> sessions = sessionRepository.findBySurveyIdAndStatus(id, status);
            if (sessions != null && !sessions.isEmpty()) {
                sessionRepository.deleteAll(sessions);
                log.info("Deleted {} {} sessions for survey {}", sessions.size(), status, id);
            }
        }

        surveyRepository.delete(survey);
        log.info("Survey {} deleted by {}", id, userId);
    }

    // ─────────────────────────────────────────
    // Check active sessions after schema update
    // ─────────────────────────────────────────
    private void checkActiveSessions(Survey newDAG) {
        List<UserSession> activeSessions = sessionRepository
                .findBySurveyIdAndStatus(newDAG.getId(), UserSession.SessionStatus.IN_PROGRESS);

        for (UserSession session : activeSessions) {
            if (session.getSurveyVersion() < newDAG.getVersion()) {
                // Run GBCR
                com.ares.backend.dto.ConflictResolution resolution = gbcrAlgorithm.resolveConflict(session, newDAG);

                log.warn("Conflict detected for session {} → {}",
                        session.getSessionId(), resolution.getType());

                // Push via WebSocket
                messagingTemplate.convertAndSend(
                        "/topic/survey/" + newDAG.getId(),
                        java.util.Map.of(
                                "event", "SCHEMA_CONFLICT",
                                "sessionId", session.getSessionId(),
                                "newVersion", newDAG.getVersion(),
                                "stableNode", resolution.getStableNode(),
                                "zombieNodes", resolution.getZombieNodes(),
                                "resolutionType", resolution.getType()));

                // Update session status
                session.setStatus(UserSession.SessionStatus.CONFLICT_DETECTED);
                sessionRepository.save(session);
            }
        }
    }
}