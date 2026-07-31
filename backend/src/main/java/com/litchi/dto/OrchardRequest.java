package com.litchi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrchardRequest {
    @NotBlank(message = "果园名称不能为空")
    @Size(max = 128, message = "果园名称不能超过 128 个字符")
    private String name;

    @Size(max = 255, message = "位置不能超过 255 个字符")
    private String location;

    @Size(max = 64, message = "品种不能超过 64 个字符")
    private String variety;

    @Size(max = 64, message = "生育期不能超过 64 个字符")
    private String growthStage;

    @DecimalMin(value = "0", message = "面积不能为负数")
    private BigDecimal areaMu;
}
