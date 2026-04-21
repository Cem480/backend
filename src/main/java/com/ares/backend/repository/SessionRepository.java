package com.ares.backend.repository;

import com.ares.backend.model.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends MongoRepository<UserSession, String> {
    List<UserSession> findBySurveyIdAndStatus(String surveyId, UserSession.SessionStatus status);

    List<UserSession> findBySurveyId(String surveyId);

}