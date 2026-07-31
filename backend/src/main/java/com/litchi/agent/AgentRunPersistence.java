package com.litchi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.AgentRunResponse;
import com.litchi.service.MysqlStateStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentRunPersistence {
    private final MysqlStateStoreService mysqlStateStoreService;
    private final ObjectMapper objectMapper;

    public void save(String ownerId, AgentRunResponse response) {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            mysqlStateStoreService.saveAgentRun(ownerId, objectMapper.writeValueAsString(response), response);
        } catch (Exception ignored) {
            // The in-memory run store remains the availability fallback.
        }
    }

    public Optional<AgentRunResponse> load(String ownerId, String runId) {
        if (!mysqlStateStoreService.isActive()) {
            return Optional.empty();
        }
        try {
            return mysqlStateStoreService.loadAgentRun(ownerId, runId)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, AgentRunResponse.class);
                        } catch (Exception exception) {
                            return null;
                        }
                    });
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void publishTerminalEvent(AgentRunResponse response) {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            String status = response.getStatus();
            String eventType = "agent.run." + status;
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> summary = Map.of(
                    "runId", response.getRunId(),
                    "status", response.getStatus(),
                    "goalLength", response.getGoal() == null ? 0 : response.getGoal().length(),
                    "durationMs", response.getDurationMs(),
                    "degraded", response.isDegraded(),
                    "riskLevel", response.getRiskLevel() == null ? "unknown" : response.getRiskLevel(),
                    "reviewRequired", response.isReviewRequired(),
                    "stepCount", response.getSteps() == null ? 0 : response.getSteps().size(),
                    "steps", response.getSteps() == null ? List.of() : response.getSteps().stream()
                            .map(step -> Map.of("tool", step.getTool(), "status", step.getStatus(), "durationMs", step.getDurationMs()))
                            .collect(Collectors.toList())
            );
            String eventJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", eventType,
                    "schemaVersion", 1,
                    "tenantId", "tenant-default",
                    "occurredAt", Instant.now().toString(),
                    "traceId", response.getRunId(),
                    "payload", summary
            ));
            mysqlStateStoreService.saveOutboxEvent(
                    eventId,
                    eventType,
                    1,
                    "tenant-default",
                    response.getRunId(),
                    eventJson,
                    Instant.now().toString()
            );
        } catch (Exception ignored) {
            // Outbox failure is observable through the persistence health metric; request availability remains intact.
        }
    }
}
