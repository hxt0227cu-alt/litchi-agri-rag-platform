package com.litchi.service;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.auth.AuthService;
import com.litchi.dto.ConsultationRecordDto;
import com.litchi.dto.CreateConsultationRequest;
import com.litchi.dto.PageResponse;
import com.litchi.dto.RemedyPlanDto;
import com.litchi.dto.SubmitFeedbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示协同数据一键初始化。
 *
 * <p>用于让“农户求助 -> 门店跟进 -> 满意度反馈”的主链路在空数据库上也有内容可演示，
 * 避免协同闭环、高频病症和反馈汇总页面始终停留在空状态。数据仅写入演示账号之间的流转。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoCollaborationService {

    private final AuthService authService;
    private final CollaborationService collaborationService;
    private final FeedbackService feedbackService;

    public synchronized Map<String, Object> seedDemoCollaboration() {
        Map<String, Object> report = new LinkedHashMap<>();
        AuthenticatedUser farmer = requireUser("farmer");
        AuthenticatedUser shopkeeper = requireUser("shopkeeper");

        List<ConsultationRecordDto> existing = collaborationService.listMyConsultations(farmer, 1, 100).getItems();
        report.put("existingConsultations", existing.size());
        if (existing.isEmpty()) {
            seedOneConsultation(farmer, shopkeeper, report);
        }

        feedbackService.submit(farmer, buildDemoFeedback());
        report.put("feedbackSeeded", true);

        log.info("Demo collaboration data seeded: {}", report);
        return report;
    }

    private void seedOneConsultation(
            AuthenticatedUser farmer,
            AuthenticatedUser shopkeeper,
            Map<String, Object> report
    ) {
        PageResponse<RemedyPlanDto> plans = collaborationService.listPlans(shopkeeper, 1, 100);
        RemedyPlanDto plan = plans.getItems().stream()
                .filter(RemedyPlanDto::isActive)
                .findFirst()
                .orElse(null);
        if (plan == null) {
            report.put("consultationCreated", false);
            report.put("reason", "没有可用的启用方案，请先在门店端新增方案。");
            return;
        }

        CreateConsultationRequest request = new CreateConsultationRequest();
        request.setPlanId(plan.getId());
        request.setDiseaseTag("炭疽病");
        request.setStageTag("果实期");
        request.setQuestion("连续阴雨后果面出现褐色病斑，想确认怎么处理更稳妥。");
        request.setReasonTags(List.of("雨季高湿", "果面病斑"));

        ConsultationRecordDto consultation = collaborationService.createConsultation(farmer, request);
        collaborationService.updateConsultationStatus(shopkeeper, consultation.getId(), "contacted");
        report.put("consultationCreated", true);
        report.put("consultationId", consultation.getId());
    }

    private SubmitFeedbackRequest buildDemoFeedback() {
        SubmitFeedbackRequest feedback = new SubmitFeedbackRequest();
        feedback.setModule("智能问答");
        feedback.setOverallScore(4);
        feedback.setAccuracyScore(4);
        feedback.setPracticalityScore(5);
        feedback.setFluencyScore(5);
        feedback.setComment("识别带出病症标签后直接进入方案推荐，链路顺畅。");
        return feedback;
    }

    private AuthenticatedUser requireUser(String username) {
        return authService.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("演示账号不存在: " + username));
    }
}
