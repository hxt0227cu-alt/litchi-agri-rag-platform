package com.litchi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.AgentRunRequest;
import com.litchi.dto.AgentRunResponse;
import com.litchi.service.LLMService;
import com.litchi.service.MysqlStateStoreService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    @Test
    void executesOnlyModelSelectedWhitelistedTools() {
        LLMService llmService = mock(LLMService.class);
        AgentTool knowledgeTool = tool("knowledge_search", true);
        AgentTool graphTool = tool("knowledge_graph", true);
        when(llmService.generateWithContext(anyString(), anyString()))
                .thenReturn("{\"steps\":[{\"tool\":\"knowledge_search\",\"reason\":\"先找依据\"},{\"tool\":\"shell\",\"reason\":\"越权工具\"}]}")
                .thenReturn("依据知识库，应先清园并加强通风。");

        AgentService service = service(List.of(knowledgeTool, graphTool), llmService);
        AgentRunResponse response = service.run(request(4), farmer());

        assertEquals("completed", response.getStatus());
        assertEquals(1, response.getSteps().size());
        assertEquals("knowledge_search", response.getSteps().get(0).getTool());
        assertFalse(response.isDegraded());
        assertEquals("model", response.getUsage().get("plannerMode"));
        assertEquals("java-agent-v2", response.getCheckpoint().get("workflowVersion"));
        assertEquals(1, response.getCheckpoint().get("currentStep"));
        assertEquals(List.of("knowledge_search"), response.getCheckpoint().get("completedTools"));
    }

    @Test
    void fallsBackToSafePlanWhenPlannerOutputIsInvalid() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.generateWithContext(anyString(), anyString()))
                .thenReturn("not-json")
                .thenReturn("已依据检索结果给出建议。");

        AgentService service = service(List.of(
                tool("knowledge_search", true),
                tool("knowledge_graph", true),
                tool("plan_recommendation", true)
        ), llmService);
        AgentRunResponse response = service.run(request(2), farmer());

        assertEquals(2, response.getSteps().size());
        assertTrue(response.isDegraded());
        assertEquals("fallback", response.getUsage().get("plannerMode"));
    }

    @Test
    void excludesToolsNotAllowedForRole() {
        LLMService llmService = mock(LLMService.class);
        when(llmService.generateWithContext(anyString(), anyString()))
                .thenReturn("{\"steps\":[{\"tool\":\"plan_recommendation\",\"reason\":\"找方案\"}]}")
                .thenReturn("没有执行未授权工具。");

        AgentService service = service(List.of(
                tool("knowledge_search", true),
                tool("plan_recommendation", false)
        ), llmService);
        AgentRunResponse response = service.run(request(4), shopkeeper());

        assertEquals(1, response.getSteps().size());
        assertEquals("knowledge_search", response.getSteps().get(0).getTool());
        assertTrue(response.isDegraded());
    }

    @Test
    void pausesForApprovalAndResumesTheApprovedWriteTool() {
        LLMService llmService = mock(LLMService.class);
        AgentTool pending = tool("pending_remedy_plan", true);
        when(pending.requiresApproval()).thenReturn(true);
        when(pending.preview(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("preview", true, "requiresApproval", true));
        when(pending.execute(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("saved", true, "planId", "plan-1"));
        when(llmService.generateWithContext(anyString(), anyString()))
                .thenReturn("{\"steps\":[{\"tool\":\"pending_remedy_plan\",\"reason\":\"生成待审核方案\"}]}")
                .thenReturn("审批通过后已保存方案。");

        AgentService service = service(List.of(pending), llmService);
        AgentRunRequest request = request(2);
        request.setGoal("生成待审核处置方案");
        AgentRunResponse waiting = service.run(request, technician());

        assertEquals("waiting_approval", waiting.getStatus());
        assertTrue(waiting.isReviewRequired());
        assertEquals("pending_remedy_plan", waiting.getCheckpoint().get("pendingTool"));
        assertTrue(Boolean.TRUE.equals(waiting.getUsage().get("writeToolsEnabled")));

        AgentRunResponse completed = service.confirm(waiting.getRunId(), "approve", technician());
        assertEquals("completed", completed.getStatus());
        assertEquals("succeeded", completed.getSteps().get(0).getStatus());
        assertEquals(Map.of("saved", true, "planId", "plan-1"), completed.getSteps().get(0).getOutput());
    }

    private AgentService service(List<AgentTool> tools, LLMService llmService) {
        AgentService service = new AgentService(
                tools,
                llmService,
                new ObjectMapper(),
                new AgentRunStore(),
                new AgentRunPersistence(mock(MysqlStateStoreService.class), new ObjectMapper()),
                new AgentEventBus(),
                new AgentMetrics(new SimpleMeterRegistry()),
                new AgentRiskPolicy()
        );
        setMaxSteps(service, 4);
        return service;
    }

    private void setMaxSteps(AgentService service, int value) {
        try {
            var field = AgentService.class.getDeclaredField("configuredMaxSteps");
            field.setAccessible(true);
            field.setInt(service, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private AgentTool tool(String name, boolean supported) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " description");
        when(tool.supports(org.mockito.ArgumentMatchers.any())).thenReturn(supported);
        when(tool.execute(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("count", 1));
        return tool;
    }

    private AgentRunRequest request(int maxSteps) {
        AgentRunRequest request = new AgentRunRequest();
        request.setGoal("雨季荔枝叶片出现褐色病斑，给出研判和处理建议");
        request.setSessionId("agent-test");
        request.setMaxSteps(maxSteps);
        return request;
    }

    private AuthenticatedUser farmer() {
        return new AuthenticatedUser("farmer-1", "farmer", "farmer", "2026-07-22T00:00:00Z");
    }

    private AuthenticatedUser shopkeeper() {
        return new AuthenticatedUser("shop-1", "shopkeeper", "shopkeeper", "2026-07-22T00:00:00Z");
    }

    private AuthenticatedUser technician() {
        return new AuthenticatedUser("tech-1", "technician", "technician", "2026-07-22T00:00:00Z");
    }
}
