package com.litchi.dto;

import com.litchi.dto.ChatResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRecordDto {
    private String id;
    private String sessionId;
    private String type;
    private String question;
    private String referenceAnswer;
    private String systemAnswer;
    private Double autoScore;
    private EvaluationRubricScore scoreBreakdown;
    private Double bleuScore;
    private Integer humanScore;
    private String reviewNote;
    private String reviewStatus;
    private Integer sourceCount;
    private String suggestedAction;
    private String improvementHint;
    private List<ChatResponse.Source> sources;
    private boolean evaluated;
    private String createdAt;
}
