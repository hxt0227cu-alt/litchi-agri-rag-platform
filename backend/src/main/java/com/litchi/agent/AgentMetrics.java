package com.litchi.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class AgentMetrics {
    private final MeterRegistry registry;

    public void recordRun(String status, String plannerMode, boolean degraded, long durationMs) {
        Counter.builder("agent.runs.total")
                .description("Agent runs completed or failed")
                .tag("status", safe(status))
                .tag("planner_mode", safe(plannerMode))
                .tag("degraded", Boolean.toString(degraded))
                .register(registry)
                .increment();
        Timer.builder("agent.run.duration")
                .description("End-to-end Agent run duration")
                .tag("status", safe(status))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTool(String tool, String status, long durationMs) {
        Counter.builder("agent.tool.calls.total")
                .description("Agent tool calls")
                .tag("tool", safe(tool))
                .tag("status", safe(status))
                .register(registry)
                .increment();
        Timer.builder("agent.tool.duration")
                .description("Agent tool duration")
                .tag("tool", safe(tool))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
