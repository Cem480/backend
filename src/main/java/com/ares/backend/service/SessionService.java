package com.ares.backend.service;

import com.ares.backend.dto.AnswerRequest;
import com.ares.backend.model.Survey;
import com.ares.backend.model.UserSession;
import com.ares.backend.repository.SessionRepository;
import com.ares.backend.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SurveyRepository surveyRepository;
    private final GBCRAlgorithm gbcrAlgorithm;

    public UserSession startSession(String surveyId, String userId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Survey not found"));

        UserSession session = new UserSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setSurveyId(surveyId);
        session.setSurveyVersion(survey.getVersion());
        session.setAnswers(new LinkedHashMap<>()); // LinkedHashMap preserves order
        session.setExpectedNextNodes(new ArrayList<>());
        session.setStatus(UserSession.SessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.now());

        // First node is the starting stable node
        if (survey.getNodes() != null && !survey.getNodes().isEmpty()) {
            session.setLastStableNode(survey.getNodes().get(0).getId());
        }

        UserSession saved = sessionRepository.save(session);
        log.info("Session started: {} for survey {}", saved.getSessionId(), surveyId);
        return saved;
    }

    public UserSession saveAnswer(String sessionId, AnswerRequest request) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        // Record answer
        session.getAnswers().put(request.getNodeId(), request.getValue());
        session.setLastStableNode(request.getNodeId());

        // Update expected next nodes
        if (request.getExpectedNextNodeId() != null) {
            session.setExpectedNextNodes(List.of(request.getExpectedNextNodeId()));
        }

        // Check if path is complete
        Survey survey = surveyRepository.findById(session.getSurveyId())
                .orElseThrow(() -> new RuntimeException("Survey not found"));

        if (gbcrAlgorithm.validatePath(survey, session.getAnswers())) {
            session.setStatus(UserSession.SessionStatus.PATH_COMPLETE);
            log.info("Session {} path complete — Send button eligible", sessionId);
        }

        UserSession saved = sessionRepository.save(session);
        log.info("Answer saved for session {}: {}={}", sessionId, request.getNodeId(), request.getValue());
        return saved;
    }

    public UserSession submitSession(String sessionId) {
        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        session.setStatus(UserSession.SessionStatus.COMPLETED);
        UserSession saved = sessionRepository.save(session);
        log.info("Session {} submitted", sessionId);
        return saved;
    }

    public Optional<UserSession> getSession(String sessionId) {
        return sessionRepository.findById(sessionId);
    }
}