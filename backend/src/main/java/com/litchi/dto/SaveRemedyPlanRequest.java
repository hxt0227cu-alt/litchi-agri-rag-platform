package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveRemedyPlanRequest {
    @NotBlank(message = "方案标题不能为空")
    private String title;
    @NotBlank(message = "病症标签不能为空")
    private String diseaseTag;
    @NotBlank(message = "适用阶段不能为空")
    private String stageTag;
    @NotBlank(message = "方案摘要不能为空")
    private String summary;
    private List<String> products;
    private List<String> usageTips;
    private List<String> riskNotes;
    private String inventoryStatus;
    private Boolean active;
}
