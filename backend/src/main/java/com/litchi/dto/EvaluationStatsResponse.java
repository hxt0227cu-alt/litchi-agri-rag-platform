package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationStatsResponse {
    private long total;
    private long evaluated;
    private Double avgAutoScore;
    private Double avgBleuScore;
    private Double avgHumanScore;
    private long reviewed;
    private long reviewPending;
    private long lowScoreCount;
    private Double recentAvgAutoScore;
    private Double previousAvgAutoScore;
    private Double scoreTrendDelta;
    private List<TypeStat> byType;
    private List<ActiveFeedbackRule> activeFeedbackRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeStat {
        private String type;
        private long count;
        private Double avgAutoScore;
        private Double avgBleuScore;
        private Double avgHumanScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveFeedbackRule {
        private String id;
        private String category;
        private String title;
        private String instruction;
        private String sourceType;
        private int evidenceCount;
        private int priority;
    }
}
