package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchardResponse {
    private String id;
    private String tenantId;
    private String ownerId;
    private String name;
    private String location;
    private String variety;
    private String growthStage;
    private BigDecimal areaMu;
    private String createdAt;
    private String updatedAt;
}
