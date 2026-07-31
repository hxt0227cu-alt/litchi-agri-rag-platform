package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeSearchAgentTool implements AgentTool {
    private final DocumentService documentService;

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "检索荔枝种植知识库，返回可引用的资料片段";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return true;
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        List<Map<String, Object>> matches = documentService.search(query, 4).stream()
                .map(match -> Map.<String, Object>of(
                        "title", value(match.getTitle()),
                        "source", value(match.getSource()),
                        "content", value(match.getContent()),
                        "page", match.getPage() == null ? 0 : match.getPage(),
                        "score", match.getScore() == null ? 0F : match.getScore()
                ))
                .toList();
        return Map.of("matches", matches, "count", matches.size());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
