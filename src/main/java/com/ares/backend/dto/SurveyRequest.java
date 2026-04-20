package com.ares.backend.dto;

import com.ares.backend.model.ConditionalEdge;
import com.ares.backend.model.QuestionNode;
import lombok.Data;

import java.util.List;

@Data
public class SurveyRequest {
    private String title;
    private List<QuestionNode> nodes;
    private List<ConditionalEdge> edges;
}