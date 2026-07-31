package com.litchi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DemoContentService {
    private static final String AUTHORITY_DEMO_RESOURCE_PATTERN = "classpath*:demo-documents/*.md";

    public List<DemoDocument> getDemoDocuments() {
        List<DemoDocument> documents = new ArrayList<>(getInlineDemoDocuments());
        documents.addAll(loadAuthorityDemoDocuments());
        return List.copyOf(documents);
    }

    private List<DemoDocument> getInlineDemoDocuments() {
        return List.of(
                new DemoDocument(
                        "demo-anthracnose-guide.md",
                        "荔枝炭疽病防治手册",
                        "围绕炭疽病的症状识别、雨季管理和防治建议整理的平台样例文档。",
                        """
                        # 荔枝炭疽病防治手册

                        ## 典型症状
                        炭疽病多发生在高温高湿环境，叶片会出现圆形或近圆形褐色病斑，果实转色期容易出现腐烂、落果和商品性下降。

                        ## 易发条件
                        连续阴雨、果园郁闭、枝叶通风差、采后清园不到位时，病原更容易在花果期和果实膨大期快速扩展。

                        ## 防治建议
                        1. 冬季彻底清园，剪除病枝病叶，减少越冬病源。
                        2. 雨季前完成一次保护性用药，雨后尽快复查果面和叶片。
                        3. 果园保持通风透光，避免枝梢过密和湿度长期过高。
                        4. 发病初期可轮换使用咪鲜胺、苯醚甲环唑等药剂，注意安全间隔期。

                        ## 平台知识要点
                        如果用户询问“荔枝炭疽病在雨季怎么防治”，建议回答要点包括：提前预防、雨后复查、加强修剪和轮换用药。
                        """
                ),
                new DemoDocument(
                        "demo-downy-blight-guide.md",
                        "荔枝霜疫霉病速查",
                        "适合在知识图谱和问答页展示的霜疫霉病知识样本。",
                        """
                        # 荔枝霜疫霉病速查

                        ## 典型症状
                        病果表面常见褐色病斑，并伴随白色霉层；在花穗和幼果期，潮湿条件下扩散较快。

                        ## 关键诱因
                        雨季、雾天、果园排水不良和树冠郁闭都会提高霜疫霉病发生概率。

                        ## 防控策略
                        1. 做好果园排水，降低花果期叶幕湿度。
                        2. 雨季来临前安排预防性药剂喷施，重点覆盖花穗和幼果。
                        3. 发现病果后及时清理，避免病原在园内持续传播。
                        4. 可结合烯酰吗啉等药剂开展防治，并注意轮换用药。

                        ## 平台知识要点
                        如果问题涉及“霜疫霉病和炭疽病怎么区分”，可说明：霜疫霉病更常见白色霉层，雨季传播更快；炭疽病更常见圆形病斑和果实腐烂。
                        """
                ),
                new DemoDocument(
                        "demo-guiwei-management.md",
                        "桂味荔枝栽培管理要点",
                        "用于展示品种、病害和栽培技术之间的关联。",
                        """
                        # 桂味荔枝栽培管理要点

                        ## 品种特点
                        桂味属于优质中熟荔枝品种，果香明显、果肉细嫩，对树体营养和通风条件要求较高。

                        ## 管理重点
                        1. 花果期注意控梢和疏枝，保持树冠透光。
                        2. 遇到连续降雨时，要重点关注炭疽病和霜疫霉病。
                        3. 果实膨大期加强果园巡查，发现病果和虫果后及时清理。

                        ## 与平台图谱相关的结论
                        在知识图谱里，桂味可与炭疽病、霜疫霉病、通风修剪、雨季巡园等节点建立关系，方便查看“品种 - 病害 - 技术”的关联链路。
                        """
                ),
                new DemoDocument(
                        "demo-fruit-borer-guide.md",
                        "荔枝蒂蛀虫监测与防控",
                        "用于问答和知识图谱页面展示虫害防控思路。",
                        """
                        # 荔枝蒂蛀虫监测与防控

                        ## 主要危害
                        蒂蛀虫会危害花穗、幼果和近成熟果，造成虫果、落果和商品性下降。

                        ## 监测建议
                        1. 花期到幼果期增加巡园频次，重点检查花穗和果梗部位。
                        2. 果期结合诱捕和人工检查，尽早发现虫口高峰。

                        ## 防控建议
                        1. 清理虫果和落果，压低虫源基数。
                        2. 结合物候期和虫情监测结果安排防治窗口。
                        3. 与修剪、清园和合理用药配套实施，避免单一手段失效。
                        """
                )
        );
    }

    private List<DemoDocument> loadAuthorityDemoDocuments() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<DemoDocument> documents = new ArrayList<>();
        try {
            List<Resource> resources = new ArrayList<>(List.of(resolver.getResources(AUTHORITY_DEMO_RESOURCE_PATTERN)));
            resources.sort(Comparator.comparing(resource -> String.valueOf(resource.getFilename())));
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                if (fileName == null || fileName.isBlank()) {
                    continue;
                }
                String content = readResourceText(resource);
                if (content.isBlank()) {
                    continue;
                }
                documents.add(new DemoDocument(
                        fileName,
                        extractMarkdownTitle(content, stripExtension(fileName)),
                        extractMarkdownSummary(content, "权威资料摘要。"),
                        content
                ));
            }
        } catch (IOException e) {
            log.warn("Failed to load authority demo documents from {}", AUTHORITY_DEMO_RESOURCE_PATTERN, e);
        }
        return documents;
    }

    private String readResourceText(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String extractMarkdownTitle(String content, String fallback) {
        return content.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(title -> !title.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private String extractMarkdownSummary(String content, String fallback) {
        String summarySection = extractMarkdownSection(content, "## 摘要");
        if (summarySection.isBlank()) {
            summarySection = content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> !line.startsWith("来源机构"))
                    .filter(line -> !line.startsWith("发布日期"))
                    .filter(line -> !line.startsWith("原始链接"))
                    .findFirst()
                    .orElse("");
        }

        String compact = summarySection.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return fallback;
        }
        return compact.length() > 96 ? compact.substring(0, 96) + "..." : compact;
    }

    private String extractMarkdownSection(String content, String heading) {
        int start = content.indexOf(heading);
        if (start < 0) {
            return "";
        }

        start += heading.length();
        int end = content.indexOf("\n## ", start);
        if (end < 0) {
            end = content.length();
        }
        return content.substring(start, end).trim();
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    public Set<String> getManagedDemoFileNames() {
        return getDemoDocuments().stream()
                .map(DemoDocument::fileName)
                .collect(Collectors.toSet());
    }

    public List<String> getSuggestedQuestions() {
        return List.of(
                "荔枝炭疽病在雨季怎么防治？",
                "霜疫霉病和炭疽病有什么区别？",
                "桂味荔枝花果期需要注意哪些管理要点？",
                "蒂蛀虫高发期应该怎么监测和处理？"
        );
    }

    public List<String> getDemoFlow() {
        return List.of(
                "先在系统总览页确认后端、知识图谱、文档检索和识别服务状态。",
                "进入知识库管理页，查看系统已经自动准备好的平台样例文档，也可以继续追加上传材料。",
                "进入智能问答页，使用推荐问题体验基于文档检索和图谱实体增强的回答。",
                "进入知识图谱页，搜索“桂味”或“炭疽病”，查看品种、病害、药剂和技术之间的关联。",
                "进入病害识别页，选择内置样图或上传图片，查看识别结果、候选类别和处理建议。"
        );
    }

    public List<Map<String, Object>> getFallbackNodes() {
        return List.of(
                node("variety-guiwei", "LitchiVariety", Map.of(
                        "name", "桂味",
                        "origin", "广东",
                        "ripeningSeason", "6月下旬至7月上旬",
                        "description", "优质中熟荔枝品种，适合作为平台知识图谱的核心品种节点。"
                )),
                node("variety-feizixiao", "LitchiVariety", Map.of(
                        "name", "妃子笑",
                        "origin", "广东、广西",
                        "ripeningSeason", "5月下旬至6月中旬",
                        "description", "早熟品种，可用于对比不同物候期的管理重点。"
                )),
                node("disease-anthracnose", "Disease", Map.of(
                        "name", "炭疽病",
                        "symptom", "叶片圆形褐斑、果实腐烂",
                        "highSeason", "高温高湿季节",
                        "description", "荔枝花果期和果实转色期常见病害。"
                )),
                node("disease-downy", "Disease", Map.of(
                        "name", "霜疫霉病",
                        "symptom", "病果褐斑并伴随白色霉层",
                        "highSeason", "雨季和雾天",
                        "description", "雨季传播快，需要结合排水和预防性用药。"
                )),
                node("pest-borer", "Pest", Map.of(
                        "name", "蒂蛀虫",
                        "damage", "危害花穗和果梗，造成虫果与落果",
                        "description", "果期重点监测的代表性虫害。"
                )),
                node("pesticide-prochloraz", "Pesticide", Map.of(
                        "name", "咪鲜胺",
                        "type", "杀菌剂",
                        "usage", "发病初期按说明书轮换使用",
                        "description", "常用于炭疽病等真菌性病害防治。"
                )),
                node("pesticide-dimethomorph", "Pesticide", Map.of(
                        "name", "烯酰吗啉",
                        "type", "杀菌剂",
                        "usage", "雨季前后用于保护性喷施",
                        "description", "适合霜疫霉病等卵菌病害管理。"
                )),
                node("tech-pruning", "CultivationTechnique", Map.of(
                        "name", "通风修剪",
                        "bestSeason", "采后至冬季",
                        "description", "保持树冠通风透光，降低病害发生率。"
                )),
                node("tech-monitoring", "CultivationTechnique", Map.of(
                        "name", "雨季巡园",
                        "bestSeason", "花果期和雨季",
                        "description", "雨后及时检查病果、虫果和树冠湿度变化。"
                ))
        );
    }

    public List<Map<String, Object>> getFallbackEdges() {
        return List.of(
                edge("variety-guiwei", "disease-anthracnose", "HAS_DISEASE"),
                edge("variety-guiwei", "disease-downy", "HAS_DISEASE"),
                edge("variety-guiwei", "tech-pruning", "NEEDS_TECHNIQUE"),
                edge("variety-guiwei", "tech-monitoring", "NEEDS_TECHNIQUE"),
                edge("variety-feizixiao", "pest-borer", "HAS_PEST"),
                edge("pesticide-prochloraz", "disease-anthracnose", "TREATS"),
                edge("pesticide-dimethomorph", "disease-downy", "TREATS"),
                edge("tech-monitoring", "pest-borer", "PREVENTS")
        );
    }

    public Map<String, Object> getFallbackGraph(String keyword) {
        String needle = normalize(keyword);
        List<String> searchTerms = extractKnowledgeTerms(keyword);
        List<Map<String, Object>> nodes = getFallbackNodes();
        List<Map<String, Object>> edges = getFallbackEdges();

        if (needle.isBlank()) {
            return Map.of("nodes", nodes, "edges", edges);
        }

        List<Map<String, Object>> matchedNodes = nodes.stream()
                .filter(node -> matchesNode(node, needle) || matchesNode(node, searchTerms))
                .toList();

        if (matchedNodes.isEmpty()) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        Set<String> matchedIds = matchedNodes.stream()
                .map(node -> String.valueOf(node.get("id")))
                .collect(Collectors.toSet());

        List<Map<String, Object>> filteredEdges = edges.stream()
                .filter(edge -> {
                    String source = String.valueOf(edge.get("source"));
                    String target = String.valueOf(edge.get("target"));
                    return matchedIds.contains(source) || matchedIds.contains(target);
                })
                .toList();

        filteredEdges.forEach(edge -> {
            matchedIds.add(String.valueOf(edge.get("source")));
            matchedIds.add(String.valueOf(edge.get("target")));
        });

        List<Map<String, Object>> filteredNodes = nodes.stream()
                .filter(node -> matchedIds.contains(String.valueOf(node.get("id"))))
                .toList();

        return Map.of("nodes", filteredNodes, "edges", filteredEdges);
    }

    public List<Map<String, Object>> searchEntities(String text) {
        return searchEntities(text, null);
    }

    public List<Map<String, Object>> searchEntities(String text, String type) {
        String needle = normalize(text);
        String normalizedType = normalize(type);
        List<String> searchTerms = extractKnowledgeTerms(text);
        if (needle.isBlank()) {
            return getFallbackNodes().stream()
                    .filter(node -> normalizedType.isBlank() || normalize(String.valueOf(node.get("label"))).equals(normalizedType))
                    .map(node -> Map.<String, Object>of(
                            "id", node.get("id"),
                            "label", node.get("label"),
                            "properties", node.get("properties")
                    ))
                    .toList();
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        for (Map<String, Object> node : getFallbackNodes()) {
            boolean typeMatched = normalizedType.isBlank()
                    || normalize(String.valueOf(node.get("label"))).equals(normalizedType);
            if (typeMatched && (matchesNode(node, needle) || matchesNode(node, searchTerms))) {
                entities.add(Map.of(
                        "id", node.get("id"),
                        "label", node.get("label"),
                        "properties", node.get("properties")
                ));
            }
        }
        return entities;
    }

    public List<String> extractKnowledgeTerms(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        Set<String> terms = new LinkedHashSet<>();
        keywordAliases().forEach((alias, canonical) -> {
            if (normalized.contains(alias)) {
                terms.add(canonical);
            }
        });

        for (Map<String, Object> node : getFallbackNodes()) {
            Object propertiesObject = node.get("properties");
            if (!(propertiesObject instanceof Map<?, ?> properties)) {
                continue;
            }

            Object name = properties.get("name");
            if (name == null) {
                continue;
            }

            String candidate = String.valueOf(name).trim();
            if (!candidate.isBlank() && normalized.contains(normalize(candidate))) {
                terms.add(candidate);
            }
        }

        for (String token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+")) {
            String trimmed = token.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 12 && !stopWords().contains(trimmed)) {
                terms.add(trimmed);
            }
        }

        if (terms.isEmpty()) {
            terms.add(normalized);
        }
        return List.copyOf(terms);
    }

    public Map<String, Object> getEntityDetail(String entityId) {
        Map<String, Object> node = getFallbackNodes().stream()
                .filter(item -> String.valueOf(item.get("id")).equals(entityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实体不存在。"));

        List<Map<String, Object>> relations = new ArrayList<>();
        for (Map<String, Object> edge : getFallbackEdges()) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            if (!entityId.equals(source) && !entityId.equals(target)) {
                continue;
            }

            String relatedId = entityId.equals(source) ? target : source;
            getFallbackNodes().stream()
                    .filter(item -> String.valueOf(item.get("id")).equals(relatedId))
                    .findFirst()
                    .ifPresent(relatedNode -> relations.add(Map.of(
                            "type", edge.get("label"),
                            "target", Map.of(
                                    "id", relatedNode.get("id"),
                                    "label", relatedNode.get("label"),
                                    "name", ((Map<?, ?>) relatedNode.get("properties")).get("name")
                            )
                    )));
        }

        return Map.of(
                "id", node.get("id"),
                "label", node.get("label"),
                "name", ((Map<?, ?>) node.get("properties")).get("name"),
                "properties", node.get("properties"),
                "relations", relations
        );
    }

    public List<Map<String, String>> getSampleDocuments() {
        return getDemoDocuments().stream()
                .map(document -> Map.of(
                        "name", document.fileName(),
                        "title", document.title(),
                        "summary", document.summary()
                ))
                .toList();
    }

    private boolean matchesNode(Map<String, Object> node, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }

        Object propertiesObject = node.get("properties");
        if (!(propertiesObject instanceof Map<?, ?> properties)) {
            return false;
        }

        for (Object value : properties.values()) {
            if (value != null && normalize(String.valueOf(value)).contains(needle)) {
                return true;
            }
        }

        return normalize(String.valueOf(node.get("label"))).contains(needle);
    }

    private boolean matchesNode(Map<String, Object> node, List<String> needles) {
        if (needles == null || needles.isEmpty()) {
            return false;
        }
        return needles.stream().anyMatch(needle -> matchesNode(node, normalize(needle)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private Map<String, String> keywordAliases() {
        return Map.ofEntries(
                Map.entry("炭疽", "炭疽病"),
                Map.entry("霜疫霉", "霜疫霉病"),
                Map.entry("霜霉", "霜疫霉病"),
                Map.entry("桂味", "桂味"),
                Map.entry("妃子笑", "妃子笑"),
                Map.entry("蒂蛀", "蒂蛀虫"),
                Map.entry("雨季", "雨季巡园"),
                Map.entry("巡园", "雨季巡园"),
                Map.entry("修剪", "通风修剪"),
                Map.entry("咯鲜胺", "咯鲜胺"),
                Map.entry("烯酰吗啉", "烯酰吗啉")
        );
    }

    private Set<String> stopWords() {
        return Set.of(
                "荔枝", "请问", "怎么", "如何", "哪些", "什么", "问题", "需要", "可以",
                "以及", "关于", "时候", "进行", "一下", "处理", "防治", "管理", "怎么做"
        );
    }

    private Map<String, Object> node(String id, String label, Map<String, Object> properties) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("label", label);
        node.put("properties", properties);
        return node;
    }

    private Map<String, Object> edge(String source, String target, String label) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("label", label);
        return edge;
    }

    public record DemoDocument(
            String fileName,
            String title,
            String summary,
            String content
    ) {
    }
}
