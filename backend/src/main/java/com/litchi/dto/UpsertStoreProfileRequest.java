package com.litchi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpsertStoreProfileRequest {
    @NotBlank(message = "店铺名称不能为空")
    private String shopName;
    @NotBlank(message = "联系人不能为空")
    private String contactName;
    private String phone;
    private String wechat;
    private String address;
    private String serviceArea;
    private String specialties;
    private Double rating;
}
