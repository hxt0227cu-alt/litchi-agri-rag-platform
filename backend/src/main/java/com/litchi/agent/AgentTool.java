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

    /**
     * 带运行上下文的执行入口。runId 可作为幂等键传给写工具，避免重复审批/重复恢复
     * 造成重复副作用；默认实现委托给无 runId 版本，普通工具无需改动。
     */
    default Map<String, Object> execute(String query, AuthenticatedUser user, String runId) {
        return execute(query, user);
    }
}
