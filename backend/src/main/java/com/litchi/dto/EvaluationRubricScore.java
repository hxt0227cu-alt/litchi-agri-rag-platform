package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRubricScore {
    private Integer accuracyScore;
    private Integer safetyScore;
    private Integer completenessScore;
    private Integer actionabilityScore;
}
