package com.litchi.controller;

import com.litchi.dto.DiagnosisResult;
import com.litchi.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @PostMapping
    public ResponseEntity<DiagnosisResult> diagnose(@RequestParam("file") MultipartFile file) {
        DiagnosisResult result = diagnosisService.diagnose(file);
        return ResponseEntity.ok(result);
    }
}
