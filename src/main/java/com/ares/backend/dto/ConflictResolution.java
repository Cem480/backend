package com.ares.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ConflictResolution {
    private String type; // "ATOMIC_RECOVERY" or "ROLLBACK"
    private String stableNode;
    private List<String> zombieNodes;
    private int newVersion;
}