package com.litchi.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeedbackRecordDto {
    private String id;
    private String userId;
    private String username;
    private String role;
    private String module;
    private Integer overallScore;
    private Integer accuracyScore;
    private Integer practicalityScore;
    private Integer fluencyScore;
    private String comment;
    private String createdAt;
}
