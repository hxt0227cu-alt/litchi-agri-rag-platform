package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreProfileDto {
    private String shopId;
    private String ownerId;
    private String ownerUsername;
    private String shopName;
    private String contactName;
    private String phone;
    private String wechat;
    private String address;
    private String serviceArea;
    private String specialties;
    private Double rating;
    private String createdAt;
    private String updatedAt;
}
