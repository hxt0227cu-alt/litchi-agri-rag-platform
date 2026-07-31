package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopTrendDto {
    private String diseaseTag;
    private long totalConsultations;
    private long recentConsultations;
    private String latestAt;
}
