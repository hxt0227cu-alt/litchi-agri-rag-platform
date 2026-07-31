package com.litchi.agent;

import com.litchi.dto.AgentRunResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

@Component
public class AgentRunStore {
    private static final int MAX_ENTRIES = 500;
    private final Map<String, StoredRun> runs = new LinkedHashMap<>();

    public synchronized void save(String ownerId, AgentRunResponse response) {
        runs.put(response.getRunId(), new StoredRun(ownerId, response));
        while (runs.size() > MAX_ENTRIES) {
            String oldest = runs.keySet().iterator().next();
            runs.remove(oldest);
        }
    }

    public synchronized Optional<AgentRunResponse> update(String runId, String ownerId, UnaryOperator<AgentRunResponse> updater) {
        StoredRun storedRun = runs.get(runId);
        if (storedRun == null || !storedRun.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        AgentRunResponse updated = updater.apply(storedRun.response());
        runs.put(runId, new StoredRun(ownerId, updated));
        return Optional.of(updated);
    }

    public synchronized Optional<AgentRunResponse> find(String runId, String ownerId) {
        StoredRun storedRun = runs.get(runId);
        if (storedRun == null || !storedRun.ownerId().equals(ownerId)) {
            return Optional.empty();
        }
        return Optional.of(storedRun.response());
    }

    private record StoredRun(String ownerId, AgentRunResponse response) {
    }
}
