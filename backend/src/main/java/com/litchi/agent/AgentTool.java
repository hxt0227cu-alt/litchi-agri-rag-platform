package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;

import java.util.Map;

public interface AgentTool {
    String name();

    String description();

    boolean supports(AuthenticatedUser user);

    default boolean requiresApproval() {
        return false;
    }

    default Map<String, Object> preview(String query, AuthenticatedUser user) {
        return Map.of("preview", true, "message", "该工具需要人工审批后执行。");
    }

    Map<String, Object> execute(String query, AuthenticatedUser user);
}
