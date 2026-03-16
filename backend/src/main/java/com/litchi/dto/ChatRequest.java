package com.litchi.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;
    private String sessionId;
    private Boolean useKnowledgeGraph;
    private Boolean useVectorSearch;
}
