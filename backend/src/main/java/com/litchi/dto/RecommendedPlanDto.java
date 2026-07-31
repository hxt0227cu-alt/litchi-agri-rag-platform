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
public class RecommendedPlanDto {
    private String planId;
    private String shopId;
    private String shopName;
    private String contactName;
    private String phone;
    private String wechat;
    private String address;
    private String serviceArea;
    private Double rating;
    private String title;
    private String diseaseTag;
    private String stageTag;
    private String summary;
    private List<String> products;
    private List<String> usageTips;
    private List<String> riskNotes;
    private String inventoryStatus;
    private Double score;
    private List<String> reasonTags;
}
