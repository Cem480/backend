package com.ares.backend.repository;

import com.ares.backend.model.SchemaVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchemaVersionRepository extends MongoRepository<SchemaVersion, String> {
    java.util.List<SchemaVersion> findBySurveyIdOrderByVersionDesc(String surveyId);
}