package com.litchi.controller;

import com.litchi.auth.AuthRequired;
import com.litchi.auth.RoleAllowed;
import com.litchi.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/kg")
@RequiredArgsConstructor
@AuthRequired
@RoleAllowed({"technician", "farmer"})
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    @GetMapping("/visualize")
    public ResponseEntity<Map<String, Object>> visualize(@RequestParam(required = false) String keyword) {
        Map<String, Object> data = knowledgeGraphService.getVisualizationData(keyword);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String type
    ) {
        return ResponseEntity.ok(knowledgeGraphService.searchEntities(keyword, type));
    }

    @GetMapping("/entities/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String id) {
        return ResponseEntity.ok(knowledgeGraphService.getEntityDetail(id));
    }
}
