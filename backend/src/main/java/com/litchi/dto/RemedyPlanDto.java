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
public class RemedyPlanDto {
    private String id;
    private String shopId;
    private String ownerId;
    private String ownerUsername;
    private String shopName;
    private String title;
    private String diseaseTag;
    private String stageTag;
    private String summary;
    private List<String> products;
    private List<String> usageTips;
    private List<String> riskNotes;
    private String inventoryStatus;
    private boolean active;
    private String createdAt;
    private String updatedAt;
    private String idempotencyKey;
}
