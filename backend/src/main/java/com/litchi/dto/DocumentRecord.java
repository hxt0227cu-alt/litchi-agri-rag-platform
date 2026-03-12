package com.litchi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord {
    private String id;
    private String name;
    private long size;
    private String contentType;
    private String uploadTime;
    private int chunkCount;
    private boolean indexed;
    private String statusMessage;
}
