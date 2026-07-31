package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationFeedbackRuleServiceTest {

    @TempDir
    Path tempDir;

    private ChatHistoryService chatHistoryService;
    private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        MysqlStateStoreService mysqlStateStoreService = mock(MysqlStateStoreService.class);
        when(mysqlStateStoreService.isActive()).thenReturn(false);

        chatHistoryService = new ChatHistoryService(objectMapper, mysqlStateStoreService);
        ReflectionTestUtils.setField(chatHistoryService, "stateFile", tempDir.resolve("chat-history.json").toString());
        chatHistoryService.init();

        evaluationService = new EvaluationService(objectMapper, chatHistoryService);
        ReflectionTestUtils.setField(evaluationService, "stateFile", tempDir.resolve("evaluation-state.json").toString());
        evaluationService.init();
    }

    @Test
    void autoLowScoreRecordsGenerateActiveFeedbackRules() {
        saveChat("荔枝炭疽病怎么防治？", "可以处理。", List.of());

        var rules = evaluationService.getStats().getActiveFeedbackRules();

        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(rule -> "knowledge".equals(rule.getCategory())));
        assertTrue(rules.stream().allMatch(rule -> rule.getEvidenceCount() > 0));
    }

    @Test
    void humanReviewSignalsTakePriorityOverAutoRules() {
        saveChat("荔枝炭疽病怎么防治？", "可以处理。", List.of());
        String reviewedId = saveChat("荔枝炭疽病在雨季怎么防治？", highQualityAnswer(), highSupportSources());

        evaluationService.submitHumanScore(reviewedId, 1, "安全用药风险，药剂倍数没有依据。");

        var rules = evaluationService.getActiveFeedbackRules();

        assertFalse(rules.isEmpty());
        assertEquals("safety", rules.get(0).getCategory());
        assertEquals("human_review", rules.get(0).getSourceType());
        assertTrue(rules.get(0).getPriority() > 1);
    }

    @Test
    void highScoringRecordsDoNotGenerateFeedbackRules() {
        saveChat("荔枝炭疽病在雨季怎么防治？", highQualityAnswer(), highSupportSources());

        assertTrue(evaluationService.getActiveFeedbackRules().isEmpty());
    }

    private String saveChat(String question, String answer, List<ChatResponse.Source> sources) {
        chatHistoryService.save(
                "farmer-1",
                "session-1",
                question,
                ChatResponse.builder()
                        .answer(answer)
                        .sources(sources)
                        .knowledgeGraph(Map.of())
                        .build()
        );
        return chatHistoryService.getAllHistoryForEvaluation().stream()
                .filter(item -> question.equals(item.getQuestion()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private String highQualityAnswer() {
        return "炭疽病雨季可先巡园判断病斑和果面腐烂情况，再清园、排水、通风、修剪，必要时按标签登记用药并注意安全间隔期，最后拍照复核并查看方案。";
    }

    private List<ChatResponse.Source> highSupportSources() {
        return List.of(ChatResponse.Source.builder()
                .title("炭疽病雨季防治")
                .source("demo-anthracnose-guide.md")
                .content(highQualityAnswer())
                .page(1)
                .score(0.8F)
                .build());
    }
}
