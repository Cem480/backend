package com.ares.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "schema_versions")
public class SchemaVersion {

    @Id
    private String id;

    private String surveyId;
    private int version;
    private boolean breaking;
    private LocalDateTime timestamp;
    private String publishedBy;
}