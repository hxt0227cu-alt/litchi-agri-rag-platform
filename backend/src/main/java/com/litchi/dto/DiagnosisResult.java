package com.litchi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {
    private String disease;
    private BigDecimal confidence;
    private List<String> suggestions;
    private List<DiseaseInfo> diseases;
    private String engine;
    private Boolean demoMode;
    private String note;

    @JsonProperty("diseaseName")
    public String getDiseaseName() {
        return disease;
    }

    @JsonProperty("suggestion")
    public String getSuggestion() {
        if (suggestions == null || suggestions.isEmpty()) {
            return null;
        }
        return suggestions.get(0);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiseaseInfo {
        private String name;
        private BigDecimal confidence;
    }
}
