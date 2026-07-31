package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunResponse {
    private String runId;
    private String sessionId;
    private String goal;
    private String status;
    private String answer;
    private boolean degraded;
    private String riskLevel;
    private boolean reviewRequired;
    private String startedAt;
    private long durationMs;
    private List<Step> steps;
    private Map<String, Object> usage;
    private Map<String, Object> checkpoint;
    private Map<String, Object> pendingAction;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {
        private int sequence;
        private String tool;
        private String reason;
        private String status;
        private long durationMs;
        private Map<String, Object> output;
        private String error;
    }
}
