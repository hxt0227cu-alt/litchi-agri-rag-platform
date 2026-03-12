package com.litchi.controller;

import com.litchi.service.DataInitializer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final DataInitializer dataInitializer;

    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initialize(@RequestParam(defaultValue = "all") String scope) {
        DataInitializer.InitResult result = dataInitializer.initialize(scope);
        return ResponseEntity.ok(Map.of(
                "scope", result.getScope(),
                "graphInitialized", result.isKnowledgeGraphInitialized(),
                "vectorInitialized", result.isVectorStoreInitialized(),
                "message", result.getMessage()
        ));
    }
}
