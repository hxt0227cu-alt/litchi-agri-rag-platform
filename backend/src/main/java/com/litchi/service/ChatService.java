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
                answer = "当前知识库中还没有可引用的资料，请先上传文档或初始化图谱数据。";
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
        StringBuilder answer = new StringBuilder();
        answer.append("当前使用本地兜底回答模式。\n");
        answer.append("问题：").append(question).append("\n");

        if (!sources.isEmpty()) {
            ChatResponse.Source source = sources.get(0);
            answer.append("文档片段显示：").append(source.getContent()).append("\n");
        }

        Object entitiesObject = kgResult.get("entities");
        if (entitiesObject instanceof List<?> entities && !entities.isEmpty()) {
            Object first = entities.get(0);
            if (first instanceof Map<?, ?> entity) {
                answer.append("图谱命中实体：")
                        .append(entity.get("label"))
                        .append(" ")
                        .append(entity.get("properties"))
                        .append("\n");
            }
        }

        answer.append("建议：结合以上来源先做田间复核，再按病害高发期提前预防。");
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
                请严格基于提供的参考资料作答，优先给出明确、可执行的种植建议。
                如果资料不足，请直接说明“不足以判断”，不要编造来源或结论。
                回答尽量使用简洁中文，并在结尾给出一条操作建议。
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

        prompt.append("请基于以上内容回答用户问题，并尽量引用能支持答案的关键事实。");
        return prompt.toString();
    }
}
