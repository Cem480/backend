package com.ares.backend.model;

import lombok.Data;
import java.util.List;

@Data
public class QuestionNode {
    private String id;
    private QuestionType type;
    private String text;
    private List<String> options;
    private boolean required;

    public enum QuestionType {
        MCQ, RATING, OPEN
    }
}