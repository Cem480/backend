package com.ares.backend.model;

import lombok.Data;

@Data
public class ConditionalEdge {
    private String fromNodeId;
    private String toNodeId;
    private String conditionAnswer; // null = unconditional
    private boolean active;
}