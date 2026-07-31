package com.litchi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitHumanScoreRequest {
    @NotBlank(message = "评测记录标识不能为空")
    private String id;

    @Min(value = 1, message = "人工评分必须在 1 到 5 之间")
    @Max(value = 5, message = "人工评分必须在 1 到 5 之间")
    private Integer humanScore;

    private String reviewNote;
}
