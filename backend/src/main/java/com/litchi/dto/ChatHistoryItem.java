package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryItem {
    private String id;
    private String sessionId;
    private String question;
    private String answer;
    private List<ChatResponse.Source> sources;
    private Map<String, Object> knowledgeGraph;
    private String createdAt;
}
