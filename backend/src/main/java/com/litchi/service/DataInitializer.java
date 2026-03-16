package com.litchi.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInitializer {

    private final Driver neo4jDriver;
    private final VectorSearchService vectorSearchService;
    private final DemoContentService demoContentService;

    public InitResult initialize(String scope) {
        String normalizedScope = scope == null ? "all" : scope.toLowerCase(Locale.ROOT);

        boolean graphInitialized = false;
        boolean vectorInitialized = false;

        if ("all".equals(normalizedScope) || "graph".equals(normalizedScope)) {
            graphInitialized = initKnowledgeGraph();
        }

        if ("all".equals(normalizedScope) || "vector".equals(normalizedScope)) {
            vectorInitialized = initVectorStore();
        }

        String message = switch (normalizedScope) {
            case "graph" -> graphInitialized ? "知识图谱初始化完成。" : "知识图谱初始化失败。";
            case "vector" -> vectorInitialized ? "向量检索初始化完成。" : "向量检索初始化失败。";
            default -> (graphInitialized || vectorInitialized)
                    ? "系统初始化执行完成。"
                    : "系统初始化未成功，请检查依赖服务状态。";
        };

        return InitResult.builder()
                .scope(normalizedScope)
                .knowledgeGraphInitialized(graphInitialized)
                .vectorStoreInitialized(vectorInitialized)
                .message(message)
                .build();
    }

    public boolean initKnowledgeGraph() {
        try (Session session = neo4jDriver.session()) {
            session.run("CREATE INDEX IF NOT EXISTS FOR (v:LitchiVariety) ON (v.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (d:Disease) ON (d.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (p:Pest) ON (p.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (pe:Pesticide) ON (pe.name)");
            session.run("CREATE INDEX IF NOT EXISTS FOR (t:CultivationTechnique) ON (t.name)");

            mergeDemoGraph(session);
            log.info("Knowledge graph demo data merged successfully");
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize knowledge graph", e);
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeDemoGraph(Session session) {
        for (Map<String, Object> node : demoContentService.getFallbackNodes()) {
            String id = String.valueOf(node.get("id"));
            String label = String.valueOf(node.get("label"));
            Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) node.get("properties"));
            properties.put("id", id);

            String cypher = "MERGE (n:" + label + " {id: $id}) SET n += $properties";
            session.run(cypher, Map.of(
                    "id", id,
                    "properties", properties
            ));
        }

        for (Map<String, Object> edge : demoContentService.getFallbackEdges()) {
            String relation = String.valueOf(edge.get("label"));
            String cypher = """
                    MATCH (source {id: $sourceId})
                    MATCH (target {id: $targetId})
                    MERGE (source)-[r:%s]->(target)
                    """.formatted(relation);
            session.run(cypher, Map.of(
                    "sourceId", edge.get("source"),
                    "targetId", edge.get("target")
            ));
        }
    }

    public boolean initVectorStore() {
        try {
            return vectorSearchService.initCollection();
        } catch (Exception e) {
            log.error("Failed to initialize vector store", e);
            return false;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitResult {
        private String scope;
        private boolean knowledgeGraphInitialized;
        private boolean vectorStoreInitialized;
        private String message;
    }
}
