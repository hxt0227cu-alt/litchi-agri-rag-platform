package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRecordDto {
    private long id;
    private String type;
    private String question;
    private String referenceAnswer;
    private String systemAnswer;
    private Double bleuScore;
    private Integer humanScore;
    private boolean evaluated;
    private String createdAt;
}
