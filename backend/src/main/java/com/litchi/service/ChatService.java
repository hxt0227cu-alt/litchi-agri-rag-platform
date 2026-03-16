package com.litchi.service;

import com.litchi.dto.ChatRequest;
import com.litchi.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final KnowledgeGraphService knowledgeGraphService;
    private final DocumentService documentService;
    private final LLMService llmService;

    public ChatResponse processChat(ChatRequest request) {
        String question = request.getQuestion();
        log.info("Processing chat request: {}", question);

        try {
            List<ChatResponse.Source> sources = buildSources(question);
            Map<String, Object> kgResult = knowledgeGraphService.queryByText(question);
            Map<String, Object> knowledgeGraph = Map.of(
                    "entities", kgResult.getOrDefault("entities", List.of())
            );

            String answer;
            if (sources.isEmpty() && ((List<?>) knowledgeGraph.get("entities")).isEmpty()) {
                answer = "当前知识库里还没有可引用的资料。你可以先在“知识库管理”页导入答辩样例文档，或再上传一份新的种植资料。";
            } else {
                String systemPrompt = buildSystemPrompt();
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

    private String buildFallbackAnswer(String question, List<ChatResponse.Source> sources, Map<String, Object> kgResult) {
        List<String> entityNames = extractEntityNames(kgResult);
        StringBuilder answer = new StringBuilder();
        answer.append("当前使用本地答辩模式生成回答。\n");
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
        answer.append(" 答辩展示时可以同时打开来源卡片，说明结论如何由知识库和图谱共同支持。");
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

    private String buildSystemPrompt() {
        return """
                你是一名荔枝种植问答助手。
                请严格基于提供的参考资料作答，优先给出明确、可执行的管理建议。
                如果资料不足，请直接说明“不足以判断”，不要编造来源或结论。
                回答请使用简洁中文，并在结尾给出一条操作建议。
                """;
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
