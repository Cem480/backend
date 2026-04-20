package com.ares.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "surveys")
public class Survey {

    @Id
    private String id;

    private String title;
    private int version;
    private SurveyStatus status;

    private List<QuestionNode> nodes;
    private List<ConditionalEdge> edges;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum SurveyStatus {
        DRAFT, LIVE
    }
}