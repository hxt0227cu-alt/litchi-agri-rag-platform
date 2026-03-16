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
    private final DemoContentService demoContentService;

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

    public boolean isNeo4jAvailable() {
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

        if (nodes.isEmpty()) {
            return demoContentService.getFallbackGraph(keyword);
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

        if (entities.isEmpty()) {
            return queryFallback(text);
        }

        result.put("entities", entities);
        return result;
    }

    private Map<String, Object> loadFallbackGraph(String keyword) {
        return demoContentService.getFallbackGraph(keyword);
    }

    private Map<String, Object> queryFallback(String text) {
        return Map.of("entities", demoContentService.searchEntities(text));
    }
}
