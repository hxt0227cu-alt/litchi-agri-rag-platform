package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在 3 到 32 个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码长度不能少于 6 位")
    private String password;
}
