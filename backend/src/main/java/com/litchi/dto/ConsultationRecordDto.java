package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationRecordDto {
    private String id;
    private String farmerUserId;
    private String farmerUsername;
    private String diseaseTag;
    private String stageTag;
    private String question;
    private String planId;
    private String planTitle;
    private String shopId;
    private String shopName;
    private String contactName;
    private String phone;
    private String wechat;
    private String status;
    private List<String> reasonTags;
    private String createdAt;
    private String updatedAt;
}
