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
    private Double avgBleuScore;
    private Double avgHumanScore;
    private List<TypeStat> byType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeStat {
        private String type;
        private long count;
        private Double avgBleuScore;
        private Double avgHumanScore;
    }
}
