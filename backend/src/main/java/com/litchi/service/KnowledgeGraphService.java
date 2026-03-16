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
        return Map.of("entities", searchEntities(text, null));
    }

    public boolean isNeo4jAvailable() {
        try (Session session = neo4jDriver.session()) {
            session.run("RETURN 1").consume();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> searchEntities(String keyword, String type) {
        if (!isNeo4jAvailable()) {
            return demoContentService.searchEntities(keyword, type);
        }

        try (Session session = neo4jDriver.session()) {
            return searchEntitiesFromNeo4j(session, keyword, type);
        } catch (Exception e) {
            log.warn("Neo4j unavailable, using fallback entity search", e);
            return demoContentService.searchEntities(keyword, type);
        }
    }

    public Map<String, Object> getEntityDetail(String entityId) {
        if (!isNeo4jAvailable()) {
            return demoContentService.getEntityDetail(entityId);
        }

        try (Session session = neo4jDriver.session()) {
            return getEntityDetailFromNeo4j(session, entityId);
        } catch (Exception e) {
            log.warn("Neo4j unavailable, using fallback entity detail", e);
            return demoContentService.getEntityDetail(entityId);
        }
    }

    private Map<String, Object> loadFromNeo4j(Session session, String keyword) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        String cypher;
        List<String> searchTerms = prepareSearchTerms(keyword);
        if (keyword != null && !keyword.isBlank()) {
            cypher = """
                MATCH (n)
                WHERE any(term IN $terms WHERE coalesce(n.name, '') CONTAINS term OR coalesce(n.description, '') CONTAINS term)
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
                ? session.run(cypher, Map.of("terms", searchTerms))
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

    private List<Map<String, Object>> searchEntitiesFromNeo4j(Session session, String keyword, String type) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedType = type == null ? "" : type.trim();
        List<String> searchTerms = prepareSearchTerms(keyword);

        StringBuilder cypher = new StringBuilder("""
            MATCH (n)
            WHERE ($keyword = '' OR any(term IN $terms WHERE
                coalesce(n.name, '') CONTAINS term
                OR coalesce(n.description, '') CONTAINS term
                OR coalesce(n.symptom, '') CONTAINS term
                OR coalesce(n.damage, '') CONTAINS term
                OR coalesce(n.usage, '') CONTAINS term
            ))
            """);
        if (!normalizedType.isBlank()) {
            cypher.append(" AND $type IN labels(n)");
        }
        cypher.append("""
            RETURN elementId(n) AS id, n
            LIMIT 50
            """);

        Result result = session.run(cypher.toString(), Map.of(
                "keyword", normalizedKeyword,
                "terms", searchTerms,
                "type", normalizedType
        ));

        List<Map<String, Object>> entities = new ArrayList<>();
        while (result.hasNext()) {
            Record record = result.next();
            var node = record.get("n").asNode();
            entities.add(Map.of(
                    "id", record.get("id").asString(),
                    "label", node.labels().iterator().next(),
                    "properties", node.asMap()
            ));
        }

        if (entities.isEmpty()) {
            return demoContentService.searchEntities(keyword, type);
        }
        return entities;
    }

    private Map<String, Object> getEntityDetailFromNeo4j(Session session, String entityId) {
        String cypher = """
            MATCH (n)
            WHERE elementId(n) = $id OR n.id = $id
            OPTIONAL MATCH (n)-[r]-(m)
            RETURN n, collect({
                type: type(r),
                target: {
                    id: elementId(m),
                    label: head(labels(m)),
                    name: m.name
                }
            }) AS relations
            LIMIT 1
            """;

        Result result = session.run(cypher, Map.of("id", entityId));
        if (!result.hasNext()) {
            return demoContentService.getEntityDetail(entityId);
        }

        Record record = result.next();
        var node = record.get("n").asNode();
        return Map.of(
                "id", node.elementId(),
                "label", node.labels().iterator().next(),
                "name", node.asMap().get("name"),
                "properties", node.asMap(),
                "relations", record.get("relations").asList()
        );
    }

    private Map<String, Object> loadFallbackGraph(String keyword) {
        return demoContentService.getFallbackGraph(keyword);
    }

    private List<String> prepareSearchTerms(String text) {
        List<String> terms = demoContentService.extractKnowledgeTerms(text);
        if (!terms.isEmpty()) {
            return terms;
        }
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.trim());
    }
}
