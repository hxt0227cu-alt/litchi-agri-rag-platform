package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.OrchardResponse;
import com.litchi.service.OrchardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrchardContextAgentTool implements AgentTool {
    private final OrchardService orchardService;

    @Override
    public String name() {
        return "orchard_context";
    }

    @Override
    public String description() {
        return "读取当前用户授权的果园、品种和生育期上下文";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return user != null && ("farmer".equals(user.role()) || "technician".equals(user.role()));
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        List<Map<String, Object>> orchards = orchardService.list(user).stream()
                .map(this::toMap)
                .toList();
        return Map.of("orchards", orchards, "count", orchards.size());
    }

    private Map<String, Object> toMap(OrchardResponse orchard) {
        return Map.of(
                "id", orchard.getId(),
                "name", orchard.getName(),
                "location", orchard.getLocation() == null ? "" : orchard.getLocation(),
                "variety", orchard.getVariety() == null ? "" : orchard.getVariety(),
                "growthStage", orchard.getGrowthStage() == null ? "" : orchard.getGrowthStage()
        );
    }
}
