package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.ConsultationRecordDto;
import com.litchi.dto.CreateConsultationRequest;
import com.litchi.dto.RecommendedPlanDto;
import com.litchi.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 写工具：当问题复杂或证据不足时，自动为农户创建求助工单并推送给门店协同处理。
 * 与只读工具的本质区别：会真实改变系统状态（生成一条门店可见的求助记录）。
 * 仅农户可用，无需审批（农户在 Agent 页面发起任务即视为授权）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationAgentTool implements AgentTool {

    private final CollaborationService collaborationService;

    @Override
    public String name() {
        return "create_consultation";
    }

    @Override
    public String description() {
        return "当问题复杂、证据不足或用户要求联系门店时，自动创建求助工单并推送给门店协同处理；仅农户可用，会真实生成一条求助记录";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return user != null && "farmer".equalsIgnoreCase(user.role());
    }

    @Override
    public boolean requiresApproval() {
        return false;
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        return execute(query, user, null);
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user, String runId) {
        try {
            List<RecommendedPlanDto> plans = collaborationService.getRecommendations(null, null, query, user);
            if (plans == null || plans.isEmpty()) {
                return Map.of("created", false, "reason", "暂无可用的门店方案，无法创建求助工单");
            }
            RecommendedPlanDto plan = plans.get(0);
            CreateConsultationRequest request = new CreateConsultationRequest();
            request.setPlanId(plan.getPlanId());
            request.setDiseaseTag(plan.getDiseaseTag());
            request.setStageTag(plan.getStageTag());
            request.setQuestion(truncate(query, 500));
            request.setReasonTags(List.of("Agent 自动发起", plan.getTitle()));
            ConsultationRecordDto created = collaborationService.createConsultation(user, request);
            log.info("agent_create_consultation runId={} userId={} planId={} consultationId={}",
                    runId, user.id(), plan.getPlanId(), created.getId());
            return Map.of(
                    "created", true,
                    "consultationId", created.getId(),
                    "status", created.getStatus(),
                    "planTitle", plan.getTitle(),
                    "shopName", plan.getShopName(),
                    "diseaseTag", plan.getDiseaseTag() != null ? plan.getDiseaseTag() : "未分类"
            );
        } catch (Exception e) {
            log.warn("agent_create_consultation failed runId={}: {}", runId, e.getMessage());
            return Map.of("created", false, "reason", "创建求助失败：" + e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
