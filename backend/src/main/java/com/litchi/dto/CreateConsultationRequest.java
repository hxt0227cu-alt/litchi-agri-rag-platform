package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateConsultationRequest {
    @NotBlank(message = "方案 ID 不能为空")
    private String planId;
    private String diseaseTag;
    private String stageTag;
    private String question;
    private List<String> reasonTags;
}
