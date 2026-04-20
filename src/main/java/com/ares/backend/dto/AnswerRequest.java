package com.ares.backend.dto;

import lombok.Data;

@Data
public class AnswerRequest {
    private String nodeId;
    private String value;
    private String expectedNextNodeId; // mobile tells backend what it expected next
}