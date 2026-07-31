package com.litchi.agent;

import com.litchi.dto.AgentRunResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AgentEventBus {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(ignored -> remove(runId, emitter));
        return emitter;
    }

    public void publish(String runId, AgentRunResponse response) {
        List<SseEmitter> emitters = subscribers.getOrDefault(runId, new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("agent-status").data(response));
                if (isTerminal(response.getStatus())) {
                    emitter.complete();
                    remove(runId, emitter);
                }
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                remove(runId, emitter);
            }
        }
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status)
                || "degraded".equals(status) || "canceled".equals(status);
    }

    private void remove(String runId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = subscribers.get(runId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                subscribers.remove(runId, emitters);
            }
        }
    }
}
