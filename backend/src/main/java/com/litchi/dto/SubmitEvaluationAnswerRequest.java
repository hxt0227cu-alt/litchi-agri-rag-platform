package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitEvaluationAnswerRequest {
    @NotBlank(message = "评测记录标识不能为空")
    private String id;

    @NotBlank(message = "系统答案不能为空")
    private String systemAnswer;
}
