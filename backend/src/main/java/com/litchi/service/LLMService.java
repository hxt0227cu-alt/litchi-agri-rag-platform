package com.litchi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:0.5b}")
    private String ollamaModel;

    @Value("${app.llm.timeout-ms:180000}")
    private int llmTimeoutMs;

    public boolean isAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200
                    && response.statusCode() < 300
                    && (ollamaModel == null || ollamaModel.isBlank() || response.body().contains("\"name\":\"" + ollamaModel + "\""));
        } catch (Exception e) {
            log.debug("Ollama availability check failed", e);
            return false;
        }
    }

    public String generate(String prompt) {
        try {
            return chat(List.of(Map.of(
                    "role", "user",
                    "content", prompt
            )));
        } catch (Exception e) {
            log.error("Failed to generate response from LLM", e);
            return "当前模型服务不可用，请稍后重试。";
        }
    }

    public String generateWithContext(String systemPrompt, String userPrompt) {
        try {
            return chat(List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
        } catch (Exception e) {
            log.error("Failed to generate response with context", e);
            return "当前模型服务不可用，请稍后重试。";
        }
    }

    private String chat(List<Map<String, String>> messages) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", ollamaModel,
                "messages", messages,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .timeout(Duration.ofMillis(llmTimeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Ollama chat returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Ollama chat response is empty");
        }
        return content;
    }
}
