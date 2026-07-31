package com.litchi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentRunRequest {
    @NotBlank(message = "任务目标不能为空")
    @Size(max = 1000, message = "任务目标不能超过 1000 个字符")
    private String goal;

    @Size(max = 100, message = "会话标识不能超过 100 个字符")
    private String sessionId;

    @Min(value = 1, message = "最大步骤数不能小于 1")
    @Max(value = 4, message = "最大步骤数不能超过 4")
    private Integer maxSteps;
}
