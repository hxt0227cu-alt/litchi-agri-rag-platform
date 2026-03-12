package com.litchi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final Driver neo4jDriver;

    public Map<String, Object> getVisualizationData(String keyword) {
        if (!isNeo4jAvailable()) {
            return loadFallbackGraph(keyword);
        }

        try (Session session = neo4jDriver.session()) {
            return loadFromNeo4j(session, keyword);
        } catch (Exception e) {
            log.warn("Neo4j unavailable, using fallback graph data", e);
            return loadFallbackGraph(keyword);
        }
    }

    public Map<String, Object> queryByText(String text) {
        if (!isNeo4jAvailable()) {
            return queryFallback(text);
        }

        try (Session session = neo4jDriver.session()) {
            return queryFromNeo4j(session, text);
        } catch (Exception e) {
            log.warn("Neo4j unavailable, using fallback knowledge entities", e);
            return queryFallback(text);
        }
    }

    private boolean isNeo4jAvailable() {
        try (Session session = neo4jDriver.session()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> loadFromNeo4j(Session session, String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        String cypher;
        if (keyword != null && !keyword.isBlank()) {
            cypher = """
                MATCH (n)
                WHERE n.name CONTAINS $keyword
                MATCH path = (n)-[*1..2]-()
                RETURN path
                LIMIT 50
                """;
        } else {
            cypher = """
                MATCH path = (n)-[r]->(m)
                RETURN path
                LIMIT 100
                """;
        }

        Result result = keyword != null && !keyword.isBlank()
                ? session.run(cypher, Map.of("keyword", keyword))
                : session.run(cypher);

        while (result.hasNext()) {
            Record record = result.next();
            var path = record.get("path").asPath();

            path.nodes().forEach(node -> {
                String id = node.elementId();
                if (nodeIds.add(id)) {
                    Map<String, Object> nodeMap = new LinkedHashMap<>();
                    nodeMap.put("id", id);
                    nodeMap.put("label", node.labels().iterator().next());
                    nodeMap.put("properties", node.asMap());
                    nodes.add(nodeMap);
                }
            });

            path.relationships().forEach(rel -> {
                Map<String, Object> edgeMap = new LinkedHashMap<>();
                edgeMap.put("source", rel.startNodeElementId());
                edgeMap.put("target", rel.endNodeElementId());
                edgeMap.put("label", rel.type());
                edges.add(edgeMap);
            });
        }

        data.put("nodes", nodes);
        data.put("edges", edges);
        return data;
    }

    private Map<String, Object> queryFromNeo4j(Session session, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> entities = new ArrayList<>();

        String cypher = """
            MATCH (n)
            WHERE n.name CONTAINS $text OR n.description CONTAINS $text
            RETURN n
            LIMIT 20
            """;

        Result queryResult = session.run(cypher, Map.of("text", text == null ? "" : text));
        while (queryResult.hasNext()) {
            Record record = queryResult.next();
            var node = record.get("n").asNode();
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("label", node.labels().iterator().next());
            entity.put("properties", node.asMap());
            entities.add(entity);
        }

        result.put("entities", entities);
        return result;
    }

    private Map<String, Object> loadFallbackGraph(String keyword) {
        String needle = keyword == null ? "" : keyword.trim();
        List<Map<String, Object>> nodes = fallbackNodes();
        List<Map<String, Object>> edges = fallbackEdges();

        if (needle.isEmpty()) {
            return Map.of("nodes", nodes, "edges", edges);
        }

        Set<String> matchedIds = new HashSet<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> properties = castProperties(node.get("properties"));
            String name = String.valueOf(properties.getOrDefault("name", ""));
            String description = String.valueOf(properties.getOrDefault("description", ""));
            if (name.contains(needle) || description.contains(needle)) {
                matchedIds.add(String.valueOf(node.get("id")));
            }
        }

        List<Map<String, Object>> filteredEdges = new ArrayList<>();
        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            if (matchedIds.contains(source) || matchedIds.contains(target)) {
                matchedIds.add(source);
                matchedIds.add(target);
                filteredEdges.add(edge);
            }
        }

        List<Map<String, Object>> filteredNodes = nodes.stream()
                .filter(node -> matchedIds.contains(String.valueOf(node.get("id"))))
                .toList();

        return Map.of(
                "nodes", filteredNodes,
                "edges", filteredEdges
        );
    }

    private Map<String, Object> queryFallback(String text) {
        String needle = text == null ? "" : text.trim();
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Map<String, Object> node : fallbackNodes()) {
            Map<String, Object> properties = castProperties(node.get("properties"));
            String name = String.valueOf(properties.getOrDefault("name", ""));
            String description = String.valueOf(properties.getOrDefault("description", ""));
            if (needle.isEmpty() || name.contains(needle) || description.contains(needle)) {
                entities.add(Map.of(
                        "label", node.get("label"),
                        "properties", properties
                ));
            }
        }

        return Map.of("entities", entities);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castProperties(Object value) {
        return (Map<String, Object>) value;
    }

    private List<Map<String, Object>> fallbackNodes() {
        return List.of(
                node("variety-guiwei", "LitchiVariety", Map.of(
                        "name", "桂味",
                        "origin", "广东",
                        "description", "优质荔枝品种，果核小，果肉厚，适合答辩演示的样例实体。"
                )),
                node("disease-shuangyi", "Disease", Map.of(
                        "name", "霜疫霉病",
                        "symptom", "果实褐色病斑与白色霉层",
                        "description", "荔枝主要病害之一，雨季高发。"
                )),
                node("disease-tanju", "Disease", Map.of(
                        "name", "炭疽病",
                        "symptom", "叶片圆形病斑和果实腐烂",
                        "description", "高温高湿环境下易发病。"
                )),
                node("pesticide-xyml", "Pesticide", Map.of(
                        "name", "烯酰吗啉",
                        "description", "常用于防治霜疫霉病。"
                )),
                node("tech-prune", "CultivationTechnique", Map.of(
                        "name", "修剪技术",
                        "description", "保持树冠通风透光，降低病害发生率。"
                ))
        );
    }

    private List<Map<String, Object>> fallbackEdges() {
        return List.of(
                edge("variety-guiwei", "disease-shuangyi", "HAS_DISEASE"),
                edge("variety-guiwei", "disease-tanju", "HAS_DISEASE"),
                edge("pesticide-xyml", "disease-shuangyi", "TREATS"),
                edge("variety-guiwei", "tech-prune", "NEEDS_TECHNIQUE")
        );
    }

    private Map<String, Object> node(String id, String label, Map<String, Object> properties) {
        return new LinkedHashMap<>(Map.of(
                "id", id,
                "label", label,
                "properties", properties
        ));
    }

    private Map<String, Object> edge(String source, String target, String label) {
        return new LinkedHashMap<>(Map.of(
                "source", source,
                "target", target,
                "label", label
        ));
    }
}
