package com.ares.backend.repository;

import com.ares.backend.model.Survey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyRepository extends MongoRepository<Survey, String> {
    List<Survey> findByCreatedBy(String userId);

    List<Survey> findByStatus(Survey.SurveyStatus status);
}