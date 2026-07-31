package com.litchi.controller;

import com.litchi.auth.AuthRequired;
import com.litchi.auth.RoleAllowed;
import com.litchi.dto.UpdateStorageSettingsRequest;
import com.litchi.service.DataInitializer;
import com.litchi.service.DemoContentService;
import com.litchi.service.DiagnosisService;
import com.litchi.service.DocumentService;
import com.litchi.service.KnowledgeGraphService;
import com.litchi.service.LLMService;
import com.litchi.service.VectorSearchService;
import com.litchi.service.CollaborationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SystemController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:0.5b}")
    private String ollamaModel;

    @Value("${spring.neo4j.uri:bolt://127.0.0.1:7687}")
    private String neo4jUri;

    @Value("${milvus.collection-name:litchi_knowledge}")
    private String milvusCollectionName;

    @Value("${app.mysql.enabled:false}")
    private boolean mysqlEnabled;

    @Value("${app.mysql.url:}")
    private String mysqlUrl;

    @Value("${app.document.storage-dir:data/documents}")
    private String documentStorageDir;

    @Value("${app.document.state-file:data/document-state.json}")
    private String documentStateFile;

    @Value("${app.diagnosis.service-url:http://127.0.0.1:8090/predict}")
    private String diagnosisServiceUrl;

    @Value("${app.startup.auto-bootstrap:false}")
    private boolean startupAutoBootstrap;

    @Value("${app.startup.max-attempts:8}")
    private int startupMaxAttempts;

    @Value("${app.startup.retry-delay-ms:5000}")
    private long startupRetryDelayMs;

    private final DataInitializer dataInitializer;
    private final KnowledgeGraphService knowledgeGraphService;
    private final VectorSearchService vectorSearchService;
    private final LLMService llmService;
    private final DiagnosisService diagnosisService;
    private final DocumentService documentService;
    private final DemoContentService demoContentService;
    private final CollaborationService collaborationService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(buildHealthPayload());
    }

    @PostMapping("/system/init")
    @AuthRequired
    @RoleAllowed("technician")
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
        CollaborationService.CollaborationSummary collaborationSummary = collaborationService.getSummary();

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
                "collaboration", Map.of(
                        "activePlans", collaborationSummary.activePlans(),
                        "consultationCount", collaborationSummary.consultationCount(),
                        "pendingConsultations", collaborationSummary.pendingConsultations(),
                        "topDisease", collaborationSummary.topDisease(),
                        "avgShopRating", collaborationSummary.avgShopRating()
                ),
                "demoReady", documentService.countDocuments() > 0,
                "suggestedQuestions", demoContentService.getSuggestedQuestions(),
                "demoFlow", demoContentService.getDemoFlow()
        ));
    }

    @GetMapping("/system/settings")
    @AuthRequired
    @RoleAllowed("technician")
    public ResponseEntity<Map<String, Object>> settings() {
        return ResponseEntity.ok(buildSettingsResponse());
    }

    @PostMapping("/system/storage")
    @AuthRequired
    @RoleAllowed("technician")
    public ResponseEntity<Map<String, Object>> updateStorage(@RequestBody UpdateStorageSettingsRequest request) {
        documentService.updateStorageSettings(request.getDocumentStorageDir(), request.getDocumentStateFile());
        return ResponseEntity.ok(Map.of(
                "message", "文档存储路径已更新。",
                "settings", buildSettingsResponse()
        ));
    }

    private Map<String, Object> buildSettingsResponse() {
        Map<String, Object> environment = new LinkedHashMap<>();
        environment.put("profile", valueOrBlank(activeProfile));
        environment.put("autoBootstrap", startupAutoBootstrap);
        environment.put("startupMaxAttempts", startupMaxAttempts);
        environment.put("startupRetryDelayMs", startupRetryDelayMs);

        DocumentService.StorageSettings storageSettings = documentService.getStorageSettings();
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("mysqlEnabled", mysqlEnabled);
        storage.put("mysqlUrl", valueOrBlank(mysqlUrl));
        storage.put("neo4jUri", valueOrBlank(neo4jUri));
        storage.put("milvusCollectionName", valueOrBlank(milvusCollectionName));
        storage.put("documentStorageDir", valueOrBlank(storageSettings.documentStorageDir()));
        storage.put("documentStateFile", valueOrBlank(storageSettings.documentStateFile()));

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("ollamaBaseUrl", valueOrBlank(ollamaBaseUrl));
        services.put("ollamaModel", valueOrBlank(ollamaModel));
        services.put("diagnosisServiceUrl", valueOrBlank(diagnosisServiceUrl));
        services.put("ollamaConnected", llmService.isAvailable());
        services.put("diagnosisConnected", diagnosisService.isAvailable());

        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("documentsTotal", documentService.countDocuments());
        platform.put("documentsIndexed", documentService.countIndexedDocuments());
        platform.put("sampleQuestions", demoContentService.getSuggestedQuestions());
        platform.put("managedRoles", List.of("farmer", "technician", "shopkeeper"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("environment", environment);
        response.put("storage", storage);
        response.put("services", services);
        response.put("platform", platform);
        return response;
    }

    @PostMapping("/system/demo/bootstrap")
    @AuthRequired
    @RoleAllowed("technician")
    public ResponseEntity<Map<String, Object>> bootstrapDemo() {
        DataInitializer.InitResult result = dataInitializer.initialize("all");
        DocumentService.DemoImportResult importResult = documentService.bootstrapDemoDocuments(true);

        String message = importResult.getImported() > 0
                ? "平台样例数据已重新导入，可以直接开始使用。"
                : "平台样例数据已准备完成。";

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

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
