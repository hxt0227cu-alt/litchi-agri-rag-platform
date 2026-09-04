package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.SaveRemedyPlanRequest;
import com.litchi.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PendingRemedyPlanAgentTool implements AgentTool {
    private final CollaborationService collaborationService;

    @Override
    public String name() {
        return "pending_remedy_plan";
    }

    @Override
    public String description() {
        return "生成待技术员审批的处置方案；审批前只返回预览，不写入方案库";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return user != null && "technician".equalsIgnoreCase(user.role());
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public Map<String, Object> preview(String query, AuthenticatedUser user) {
        return Map.of(
                "preview", true,
                "action", "create_remedy_plan",
                "title", "Agent 待审核处置方案",
                "summary", summarize(query),
                "requiresApproval", true,
                "approverRole", "technician"
        );
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        return execute(query, user, null);
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user, String runId) {
        SaveRemedyPlanRequest request = new SaveRemedyPlanRequest();
        request.setTitle("Agent 待审核处置方案");
        request.setDiseaseTag("Agent 研判问题");
        request.setStageTag("待技术员复核");
        request.setSummary(summarize(query));
        request.setProducts(List.of("审批后由技术员补充具体产品"));
        request.setUsageTips(List.of("执行前核对病害、阶段和标签", "遵守产品标签与当地规范"));
        request.setRiskNotes(List.of("该方案由 Agent 生成，必须人工复核，不构成精确处方"));
        request.setInventoryStatus("待确认");
        request.setActive(false);
        // 以运行 ID 作为幂等键：同一运行被重复审批/重复恢复时只落一条方案
        request.setIdempotencyKey(runId == null || runId.isBlank() ? null : runId);
        var saved = collaborationService.createPlan(user, request);
        return Map.of("saved", true, "planId", saved.getId(), "title", saved.getTitle());
    }

    private String summarize(String query) {
        if (query == null || query.isBlank()) {
            return "请根据已收集证据补充处置方案。";
        }
        return query.length() > 240 ? query.substring(0, 240) : query;
    }
}
