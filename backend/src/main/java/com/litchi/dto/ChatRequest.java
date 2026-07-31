package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "问题不能为空")
    private String question;
    private String sessionId;
    private Boolean useKnowledgeGraph;
    private Boolean useVectorSearch;
}
