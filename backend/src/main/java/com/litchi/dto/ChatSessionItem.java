package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionItem {
    private String sessionId;
    private String title;
    private String lastMessage;
    private String updatedAt;
    private int messageCount;
}
