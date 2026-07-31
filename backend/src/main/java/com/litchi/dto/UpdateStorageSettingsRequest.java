package com.litchi.dto;

import lombok.Data;

@Data
public class UpdateStorageSettingsRequest {
    private String documentStorageDir;
    private String documentStateFile;
}
