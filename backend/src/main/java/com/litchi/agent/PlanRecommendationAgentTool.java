package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.RecommendedPlanDto;
import com.litchi.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlanRecommendationAgentTool implements AgentTool {
    private final CollaborationService collaborationService;

    @Override
    public String name() {
        return "plan_recommendation";
    }

    @Override
    public String description() {
        return "按当前问题推荐门店解决方案，仅农户和技术员可用";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return user != null && ("farmer".equals(user.role()) || "technician".equals(user.role()));
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        List<RecommendedPlanDto> plans = collaborationService.getRecommendations(null, null, query, user);
        return Map.of("plans", plans, "count", plans.size());
    }
}
