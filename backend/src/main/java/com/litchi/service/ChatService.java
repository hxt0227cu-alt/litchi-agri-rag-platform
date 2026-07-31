package com.litchi.service;

import com.litchi.dto.ChatRequest;
import com.litchi.dto.ChatResponse;
import com.litchi.dto.EvaluationStatsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private static final float OUT_OF_SCOPE_MAX_SOURCE_SCORE = 0.18F;
    private static final float STRONG_SOURCE_SCORE = 0.28F;
    private static final Pattern DOMAIN_HINT_PATTERN = Pattern.compile(
            "(?:\u8354\u679d|\u75c5\u5bb3|\u866b\u5bb3|\u70ad\u75bd|\u971c\u75ab\u9709|\u8482\u86c0\u866b|"
                    + "\u6842\u5473|\u82b1\u671f|\u679c\u671f|\u82b1\u7a57|\u5e7c\u679c|\u679c\u9762|\u53f6\u7247|"
                    + "\u75c5\u6591|\u866b\u5b54|\u843d\u679c|\u6389\u679c|\u70c2\u679c|\u9ec4\u53f6|\u679c\u56ed|"
                    + "\u679c\u6811|\u5de1\u56ed|\u96e8\u5b63|\u6392\u6c34|\u901a\u98ce|\u6e05\u56ed|\u4fdd\u679c|"
                    + "\u7528\u836f|\u6253\u836f|\u55b7\u836f|\u8bc6\u522b|\u9632\u6cbb|\u836f\u5242)"
    );
    private static final Pattern ROLEPLAY_OFF_TOPIC_PATTERN = Pattern.compile(
            "^(?:\u6211\u662f|\u5047\u5982\u4f60\u662f|\u8bf7\u626e\u6f14).{0,12}(?:\u75c5\u6bd2|\u7ec6\u83cc|\u673a\u5668\u4eba|\u5916\u661f\u4eba|\u8d85\u4eba|\u50f5\u5c38|\u602a\u517d)"
    );
    private static final Pattern GENERIC_OFF_TOPIC_PATTERN = Pattern.compile(
            "(?:\u5199\u4ee3\u7801|\u7ffb\u8bd1|\u7b11\u8bdd|\u7535\u5f71|\u80a1\u7968|\u57fa\u91d1|\u5f69\u7968|\u661f\u5ea7|\u5929\u6c14|\u604b\u7231|\u4f5c\u6587|\u5531\u6b4c)"
    );

    private final KnowledgeGraphService knowledgeGraphService;
    private final DocumentService documentService;
    private final LLMService llmService;
    private final EvaluationService evaluationService;

    public ChatResponse processChat(ChatRequest request) {
        String question = request.getQuestion();
        log.info("Processing chat request: {}", question);

        try {
            boolean useVectorSearch = request.getUseVectorSearch() == null || request.getUseVectorSearch();
            boolean useKnowledgeGraph = request.getUseKnowledgeGraph() == null || request.getUseKnowledgeGraph();

            List<ChatResponse.Source> sources = useVectorSearch ? buildSources(question) : List.of();
            Map<String, Object> kgResult = useKnowledgeGraph
                    ? knowledgeGraphService.queryByText(question)
                    : Map.of("entities", List.of());
            Map<String, Object> knowledgeGraph = Map.of(
                    "entities", kgResult.getOrDefault("entities", List.of())
            );

            String answer;
            if (isOutOfScopeQuestion(question, sources, knowledgeGraph)) {
                answer = buildOutOfScopeAnswer();
                sources = List.of();
                knowledgeGraph = Map.of("entities", List.of());
            } else if (sources.isEmpty() && ((List<?>) knowledgeGraph.get("entities")).isEmpty()) {
                answer = "当前知识库里还没有可引用的资料。你可以先在“知识库管理”页导入平台样例文档，或再上传一份新的种植资料。";
            } else {
                List<EvaluationStatsResponse.ActiveFeedbackRule> activeFeedbackRules = loadActiveFeedbackRules();
                String systemPrompt = buildSystemPrompt(activeFeedbackRules);
                if (!activeFeedbackRules.isEmpty()) {
                    log.debug("Applied {} evaluation feedback rules to chat prompt", activeFeedbackRules.size());
                }
                String userPrompt = buildUserPrompt(question, sources, kgResult);
                answer = llmService.generateWithContext(systemPrompt, userPrompt);
                if (answer == null || answer.isBlank() || answer.contains("当前模型服务不可用")) {
                    answer = buildFallbackAnswer(question, sources, kgResult);
                }
            }

            return ChatResponse.builder()
                    .answer(answer)
                    .sources(sources)
                    .knowledgeGraph(knowledgeGraph)
                    .build();
        } catch (Exception e) {
            log.error("Failed to process chat", e);
            return ChatResponse.builder()
                    .answer("处理问题时出现异常，请稍后重试。")
                    .sources(List.of())
                    .knowledgeGraph(Map.of("entities", List.of()))
                    .build();
        }
    }

    private boolean isOutOfScopeQuestion(String question, List<ChatResponse.Source> sources, Map<String, Object> knowledgeGraph) {
        if (question == null || question.isBlank()) {
            return false;
        }

        String normalizedQuestion = normalizeQuestion(question);
        boolean hasDomainCue = DOMAIN_HINT_PATTERN.matcher(normalizedQuestion).find();
        boolean hasGraphEntity = entityCount(knowledgeGraph) > 0;
        float topSourceScore = topSourceScore(sources);
        boolean weakEvidence = sources == null || sources.isEmpty() || topSourceScore < OUT_OF_SCOPE_MAX_SOURCE_SCORE;
        boolean clearlyOffTopic = ROLEPLAY_OFF_TOPIC_PATTERN.matcher(normalizedQuestion).find()
                || GENERIC_OFF_TOPIC_PATTERN.matcher(normalizedQuestion).find();

        if (hasGraphEntity || topSourceScore >= STRONG_SOURCE_SCORE) {
            return false;
        }
        if (clearlyOffTopic && weakEvidence) {
            return true;
        }
        if (hasDomainCue) {
            return false;
        }
        return weakEvidence;
    }

    private float topSourceScore(List<ChatResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return 0F;
        }

        float score = 0F;
        for (ChatResponse.Source source : sources) {
            if (source != null && source.getScore() != null) {
                score = Math.max(score, source.getScore());
            }
        }
        return score;
    }

    private int entityCount(Map<String, Object> knowledgeGraph) {
        Object entities = knowledgeGraph.get("entities");
        return entities instanceof List<?> list ? list.size() : 0;
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.replaceAll("\\s+", "");
    }

    private String buildOutOfScopeAnswer() {
        return "\u8fd9\u4e2a\u95ee\u9898\u4e0d\u5728\u5f53\u524d\u5e73\u53f0\u7684\u95ee\u7b54\u8303\u56f4\u5185\u3002\u8fd9\u91cc\u4e3b\u8981\u56de\u7b54\u8354\u679d\u75c5\u5bb3\u3001\u866b\u5bb3\u3001\u82b1\u679c\u671f\u7ba1\u7406\u3001\u96e8\u5b63\u5de1\u56ed\u548c\u5b89\u5168\u7528\u836f\u7b49\u95ee\u9898\u3002\u4f60\u53ef\u4ee5\u6362\u6210\u679c\u56ed\u73b0\u8c61\u7c7b\u95ee\u6cd5\u518d\u63d0\u95ee\uff0c\u4f8b\u5982\u201c\u8354\u679d\u53f6\u7247\u6709\u8910\u8272\u75c5\u6591\u600e\u4e48\u529e\u201d\u6216\u201c\u96e8\u5b63\u70ad\u75bd\u75c5\u600e\u4e48\u9632\u6cbb\u201d\u3002";
    }

    private String buildFallbackAnswer(String question, List<ChatResponse.Source> sources, Map<String, Object> kgResult) {
        List<String> entityNames = extractEntityNames(kgResult);
        StringBuilder answer = new StringBuilder();
        answer.append("当前使用本地保障模式生成回答。\n");
        answer.append("问题：").append(question).append("\n\n");

        if (!sources.isEmpty()) {
            answer.append("文档依据：\n");
            for (int i = 0; i < Math.min(2, sources.size()); i++) {
                ChatResponse.Source source = sources.get(i);
                answer.append("- 《")
                        .append(source.getSource())
                        .append("》提到：")
                        .append(trimSnippet(source.getContent()))
                        .append("\n");
            }
        }

        if (!entityNames.isEmpty()) {
            answer.append("图谱命中：").append(String.join("、", entityNames)).append("。\n");
        }

        answer.append("\n建议结论：");
        if (question != null && question.contains("雨")) {
            answer.append("雨季场景下要优先做好排水、通风和雨后复查，并把预防性用药放在连续降雨之前。");
        } else if (question != null && (question.contains("区别") || question.contains("区分") || question.contains("怎么分辨"))) {
            answer.append("可以先从症状差异入手，再结合发生季节和果园环境做判断，必要时同时覆盖两类病害的防控措施。");
        } else {
            answer.append("先依据文档和图谱命中的信息完成田间复核，再结合物候期安排修剪、巡园和针对性防治。");
        }
        answer.append(" 你可以同时打开来源卡片，查看结论如何由知识库和图谱共同支持。");
        return answer.toString();
    }

    private List<ChatResponse.Source> buildSources(String question) {
        List<DocumentService.ChunkMatch> matches = documentService.search(question, 4);
        List<ChatResponse.Source> sources = new ArrayList<>();
        for (DocumentService.ChunkMatch match : matches) {
            sources.add(ChatResponse.Source.builder()
                    .title(match.getTitle())
                    .content(match.getContent())
                    .source(match.getSource())
                    .page(match.getPage())
                    .score(match.getScore())
                    .build());
        }
        return sources;
    }

    private List<EvaluationStatsResponse.ActiveFeedbackRule> loadActiveFeedbackRules() {
        try {
            return evaluationService.getActiveFeedbackRules();
        } catch (Exception e) {
            log.warn("Failed to load evaluation feedback rules, using base prompt", e);
            return List.of();
        }
    }

    private String buildSystemPrompt(List<EvaluationStatsResponse.ActiveFeedbackRule> activeFeedbackRules) {
        String basePrompt = """
                你是一名荔枝种植问答助手。
                请严格基于提供的参考资料作答，优先给出明确、可执行的管理建议。
                如果资料不足，请直接说明“不足以判断”，不要编造来源或结论。
                回答请使用简洁中文，并在结尾给出一条操作建议。
                """;
        if (activeFeedbackRules == null || activeFeedbackRules.isEmpty()) {
            return basePrompt;
        }

        StringBuilder prompt = new StringBuilder(basePrompt);
        prompt.append("\n近期评测反哺要求：\n");
        for (int i = 0; i < activeFeedbackRules.size(); i++) {
            EvaluationStatsResponse.ActiveFeedbackRule rule = activeFeedbackRules.get(i);
            prompt.append(i + 1)
                    .append(". ")
                    .append(rule.getInstruction())
                    .append("\n");
        }
        return prompt.toString();
    }

    private String buildUserPrompt(String question, List<ChatResponse.Source> sources, Map<String, Object> kgResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题：").append(question).append("\n\n");

        if (!sources.isEmpty()) {
            prompt.append("参考文档片段：\n");
            for (int i = 0; i < sources.size(); i++) {
                ChatResponse.Source source = sources.get(i);
                prompt.append("[")
                        .append(i + 1)
                        .append("] 文件：")
                        .append(source.getSource())
                        .append("，片段：")
                        .append(source.getContent())
                        .append("\n");
            }
            prompt.append("\n");
        }

        Object entitiesObject = kgResult.get("entities");
        if (entitiesObject instanceof List<?> entities && !entities.isEmpty()) {
            prompt.append("知识图谱命中：\n");
            for (Object entityObject : entities) {
                if (entityObject instanceof Map<?, ?> entity) {
                    prompt.append("- ")
                            .append(entity.get("label"))
                            .append(": ")
                            .append(entity.get("properties"))
                            .append("\n");
                }
            }
            prompt.append("\n");
        }

        prompt.append("请基于以上内容回答用户问题，并尽量引用能够支撑结论的关键信息。");
        return prompt.toString();
    }

    private List<String> extractEntityNames(Map<String, Object> kgResult) {
        Object entitiesObject = kgResult.get("entities");
        if (!(entitiesObject instanceof List<?> entities)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Object entityObject : entities) {
            if (!(entityObject instanceof Map<?, ?> entity)) {
                continue;
            }

            Object propertiesObject = entity.get("properties");
            if (!(propertiesObject instanceof Map<?, ?> properties)) {
                continue;
            }

            Object name = properties.get("name");
            if (name != null) {
                names.add(String.valueOf(name));
            }
        }
        return names.stream().distinct().limit(4).toList();
    }

    private String trimSnippet(String content) {
        if (content == null) {
            return "";
        }

        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }
}
