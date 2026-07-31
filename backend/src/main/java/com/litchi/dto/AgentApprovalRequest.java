package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentApprovalRequest {
    @NotBlank(message = "审批决定不能为空")
    private String decision;

    private String comment;
}
