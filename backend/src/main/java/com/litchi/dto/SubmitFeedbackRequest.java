package com.litchi.dto;

import lombok.Data;

@Data
public class SubmitFeedbackRequest {
    private String module;
    private Integer overallScore;
    private Integer accuracyScore;
    private Integer practicalityScore;
    private Integer fluencyScore;
    private String comment;
}
