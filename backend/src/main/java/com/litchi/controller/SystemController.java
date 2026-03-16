package com.litchi.controller;

import com.litchi.service.DataInitializer;
import com.litchi.service.DemoContentService;
import com.litchi.service.DiagnosisService;
import com.litchi.service.DocumentService;
import com.litchi.service.KnowledgeGraphService;
import com.litchi.service.LLMService;
import com.litchi.service.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SystemController {

    private final DataInitializer dataInitializer;
    private final KnowledgeGraphService knowledgeGraphService;
    private final VectorSearchService vectorSearchService;
    private final LLMService llmService;
    private final DiagnosisService diagnosisService;
    private final DocumentService documentService;
    private final DemoContentService demoContentService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(buildHealthPayload());
    }

    @PostMapping("/system/init")
    public ResponseEntity<Map<String, Object>> initialize(@RequestParam(defaultValue = "all") String scope) {
        DataInitializer.InitResult result = dataInitializer.initialize(scope);
        return ResponseEntity.ok(Map.of(
                "scope", result.getScope(),
                "graphInitialized", result.isKnowledgeGraphInitialized(),
                "vectorInitialized", result.isVectorStoreInitialized(),
                "message", result.getMessage()
        ));
    }

    @GetMapping("/system/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> graph = knowledgeGraphService.getVisualizationData(null);
        List<?> nodes = (List<?>) graph.getOrDefault("nodes", List.of());
        List<?> edges = (List<?>) graph.getOrDefault("edges", List.of());
        DiagnosisService.HealthStatus diagnosisHealth = diagnosisService.getHealth();

        return ResponseEntity.ok(Map.of(
                "services", buildHealthPayload(),
                "documents", Map.of(
                        "total", documentService.countDocuments(),
                        "indexed", documentService.countIndexedDocuments(),
                        "samples", demoContentService.getSampleDocuments()
                ),
                "knowledgeGraph", Map.of(
                        "nodeCount", nodes.size(),
                        "edgeCount", edges.size()
                ),
                "diagnosis", Map.of(
                        "engine", diagnosisHealth.engine(),
                        "demoMode", diagnosisHealth.demoMode(),
                        "modelLoaded", diagnosisHealth.modelLoaded()
                ),
                "demoReady", documentService.countDocuments() > 0,
                "suggestedQuestions", demoContentService.getSuggestedQuestions(),
                "demoFlow", demoContentService.getDemoFlow()
        ));
    }

    @PostMapping("/system/demo/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrapDemo() {
        DataInitializer.InitResult result = dataInitializer.initialize("all");
        DocumentService.DemoImportResult importResult = documentService.bootstrapDemoDocuments(true);

        String message = importResult.getImported() > 0
                ? "答辩样例数据已重新导入，可以直接开始演示。"
                : "答辩样例数据已准备完成。";

        return ResponseEntity.ok(Map.of(
                "message", message,
                "graphInitialized", result.isKnowledgeGraphInitialized(),
                "vectorInitialized", result.isVectorStoreInitialized(),
                "importedDocuments", importResult.getImported(),
                "skippedDocuments", importResult.getSkipped(),
                "totalDocuments", importResult.getTotalDocuments(),
                "suggestedQuestions", demoContentService.getSuggestedQuestions()
        ));
    }

    private Map<String, Object> buildHealthPayload() {
        Map<String, String> services = new LinkedHashMap<>();

        boolean neo4jAvailable = knowledgeGraphService.isNeo4jAvailable();
        boolean milvusAvailable = vectorSearchService.isAvailable();
        boolean ollamaAvailable = llmService.isAvailable();
        DiagnosisService.HealthStatus diagnosisHealth = diagnosisService.getHealth();

        services.put("neo4j", neo4jAvailable ? "connected" : "unavailable");
        services.put("milvus", milvusAvailable ? "connected" : "unavailable");
        services.put("ollama", ollamaAvailable ? "connected" : "unavailable");
        services.put("diagnosis", diagnosisHealth.systemStatus());

        boolean healthy = neo4jAvailable && milvusAvailable && ollamaAvailable && diagnosisHealth.modelLoaded();
        return Map.of(
                "status", healthy ? "healthy" : "degraded",
                "services", services,
                "diagnosisDetails", Map.of(
                        "engine", diagnosisHealth.engine(),
                        "demoMode", diagnosisHealth.demoMode(),
                        "modelLoaded", diagnosisHealth.modelLoaded()
                ),
                "documents", Map.of(
                        "total", documentService.countDocuments(),
                        "indexed", documentService.countIndexedDocuments()
                ),
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
