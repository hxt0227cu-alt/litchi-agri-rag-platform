package com.litchi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitFeedbackRequest {
    @NotBlank(message = "反馈模块不能为空")
    private String module;

    @Min(value = 1, message = "总体满意度必须为 1 到 5 分")
    @Max(value = 5, message = "总体满意度必须为 1 到 5 分")
    private Integer overallScore;

    @Min(value = 1, message = "准确性必须为 1 到 5 分")
    @Max(value = 5, message = "准确性必须为 1 到 5 分")
    private Integer accuracyScore;

    @Min(value = 1, message = "实用性必须为 1 到 5 分")
    @Max(value = 5, message = "实用性必须为 1 到 5 分")
    private Integer practicalityScore;

    @Min(value = 1, message = "流畅性必须为 1 到 5 分")
    @Max(value = 5, message = "流畅性必须为 1 到 5 分")
    private Integer fluencyScore;

    @Size(max = 500, message = "反馈备注不能超过 500 个字符")
    private String comment;
}
