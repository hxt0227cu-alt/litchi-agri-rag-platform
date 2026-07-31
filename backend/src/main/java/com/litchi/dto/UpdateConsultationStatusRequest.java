package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateConsultationStatusRequest {
    @NotBlank(message = "状态不能为空")
    private String status;
}
