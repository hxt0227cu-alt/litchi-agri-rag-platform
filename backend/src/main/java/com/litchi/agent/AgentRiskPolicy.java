package com.litchi.agent;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class AgentRiskPolicy {
    public RiskDecision evaluate(String goal, boolean degraded, boolean toolFailure, int evidenceCount) {
        String normalized = goal == null ? "" : goal.toLowerCase(Locale.ROOT);
        boolean medication = normalized.contains("剂量") || normalized.contains("用药")
                || normalized.contains("喷施") || normalized.contains("药剂");
        boolean insufficientEvidence = evidenceCount == 0 || degraded || toolFailure;
        if (medication || insufficientEvidence) {
            return new RiskDecision("high", true);
        }
        return new RiskDecision("medium", false);
    }

    public record RiskDecision(String level, boolean reviewRequired) {
    }
}
