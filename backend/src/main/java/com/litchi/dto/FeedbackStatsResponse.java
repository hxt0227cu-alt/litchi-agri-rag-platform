package com.litchi.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FeedbackStatsResponse {
    private long total;
    private Double avgOverallScore;
    private Double avgAccuracyScore;
    private Double avgPracticalityScore;
    private Double avgFluencyScore;
    private List<ModuleStat> byModule;
    private List<FeedbackRecordDto> recent;

    @Data
    @Builder
    public static class ModuleStat {
        private String module;
        private long count;
        private Double avgOverallScore;
    }
}
