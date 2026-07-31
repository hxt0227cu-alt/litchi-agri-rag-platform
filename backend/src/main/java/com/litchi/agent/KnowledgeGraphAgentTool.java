package com.litchi.agent;

import com.litchi.auth.AuthenticatedUser;
import com.litchi.service.KnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeGraphAgentTool implements AgentTool {
    private final KnowledgeGraphService knowledgeGraphService;

    @Override
    public String name() {
        return "knowledge_graph";
    }

    @Override
    public String description() {
        return "查询荔枝品种、病虫害、药剂和栽培技术之间的关系";
    }

    @Override
    public boolean supports(AuthenticatedUser user) {
        return true;
    }

    @Override
    public Map<String, Object> execute(String query, AuthenticatedUser user) {
        return knowledgeGraphService.queryByText(query);
    }
}
