package com.litchi.service;

import com.litchi.dto.ChatRequest;
import com.litchi.dto.EvaluationStatsResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceFeedbackRuleTest {

    @Test
    void feedbackRulesAreInjectedIntoSystemPrompt() {
        LLMService llmService = mock(LLMService.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        ChatService chatService = new ChatService(
                mockKnowledgeGraphService(),
                mockDocumentService(),
                llmService,
                evaluationService
        );
        when(evaluationService.getActiveFeedbackRules()).thenReturn(List.of(
                EvaluationStatsResponse.ActiveFeedbackRule.builder()
                        .id("feedback-rule-safety")
                        .category("safety")
                        .title("收紧安全用药边界")
                        .instruction("必须先说明不足以判断，不要补编剂量或操作。")
                        .sourceType("human_review")
                        .evidenceCount(1)
                        .priority(5)
                        .build()
        ));
        when(llmService.generateWithContext(anyString(), anyString())).thenReturn("生成回答");

        chatService.processChat(chatRequest());

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(llmService).generateWithContext(systemPrompt.capture(), anyString());
        assertTrue(systemPrompt.getValue().contains("近期评测反哺要求"));
        assertTrue(systemPrompt.getValue().contains("必须先说明不足以判断"));
    }

    @Test
    void emptyFeedbackRulesKeepBaseSystemPrompt() {
        LLMService llmService = mock(LLMService.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        ChatService chatService = new ChatService(
                mockKnowledgeGraphService(),
                mockDocumentService(),
                llmService,
                evaluationService
        );
        when(evaluationService.getActiveFeedbackRules()).thenReturn(List.of());
        when(llmService.generateWithContext(anyString(), anyString())).thenReturn("生成回答");

        chatService.processChat(chatRequest());

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(llmService).generateWithContext(systemPrompt.capture(), anyString());
        assertFalse(systemPrompt.getValue().contains("近期评测反哺要求"));
    }

    private ChatRequest chatRequest() {
        ChatRequest request = new ChatRequest();
        request.setQuestion("荔枝炭疽病在雨季怎么防治？");
        request.setUseKnowledgeGraph(true);
        request.setUseVectorSearch(true);
        return request;
    }

    private KnowledgeGraphService mockKnowledgeGraphService() {
        KnowledgeGraphService service = mock(KnowledgeGraphService.class);
        when(service.queryByText(anyString())).thenReturn(Map.of("entities", List.of()));
        return service;
    }

    private DocumentService mockDocumentService() {
        DocumentService service = mock(DocumentService.class);
        when(service.search(anyString(), eq(4))).thenReturn(List.of(DocumentService.ChunkMatch.builder()
                .title("炭疽病雨季防治")
                .source("demo-anthracnose-guide.md")
                .content("炭疽病雨季应做好排水、通风、清园和安全用药。")
                .page(1)
                .score(0.8F)
                .build()));
        return service;
    }
}
