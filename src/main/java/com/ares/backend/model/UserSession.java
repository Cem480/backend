package com.ares.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "sessions")
public class UserSession {

    @Id
    private String sessionId;

    private String userId;
    private String surveyId;
    private int surveyVersion;

    private Map<String, String> answers;
    private String lastStableNode;
    private List<String> expectedNextNodes;

    private SessionStatus status;
    private LocalDateTime startedAt;

    public enum SessionStatus {
        IN_PROGRESS, CONFLICT_DETECTED, ROLLED_BACK,
        ATOMIC_RECOVERED, PATH_COMPLETE, COMPLETED
    }
}