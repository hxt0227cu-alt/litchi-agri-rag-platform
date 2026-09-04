package com.litchi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private volatile long retryAfterEpochMs;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.llm.provider:ollama}")
    private String provider;

    @Value("${app.llm.base-url:}")
    private String configuredBaseUrl;

    @Value("${app.llm.api-key:}")
    private String apiKey;

    @Value("${spring.ai.ollama.chat.options.model:qwen2.5:0.5b}")
    private String ollamaModel;

    @Value("${app.llm.timeout-ms:30000}")
    private int llmTimeoutMs;

    @Value("${app.llm.temperature:0.2}")
    private double llmTemperature;

    @Value("${app.llm.num-predict:96}")
    private int llmNumPredict;

    @Value("${app.llm.keep-alive:30m}")
    private String llmKeepAlive;

    @Value("${app.resilience.dependency-retry-delay-ms:300000}")
    private long dependencyRetryDelayMs;

        @PostConstruct
    public void warmUp() {
        try {
            boolean available = isAvailable();
            log.info("LLM availability check at startup: provider={} available={}", provider, available);
        } catch (Exception e) {
            log.warn("LLM startup availability check failed, circuit will open on first call", e);
        }
    }

public synchronized boolean isAvailable() {
        if (isCircuitOpen()) {
            return false;
        }
        try {
            String baseUrl = effectiveBaseUrl();
            String availabilityPath = isOpenAiCompatible() ? "/v1/models" : "/api/tags";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + availabilityPath))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && (ollamaModel == null || ollamaModel.isBlank() || response.body().contains(ollamaModel));
            if (available) {
                retryAfterEpochMs = 0L;
            } else {
                markUnavailable();
            }
            return available;
        } catch (Exception e) {
            markUnavailable();
            log.debug("LLM availability check failed provider={}", provider, e);
            return false;
        }
    }

    public String generate(String prompt) {
        if (isCircuitOpen()) {
            return "当前模型服务不可用，请稍后重试。";
        }
        try {
            return chat(List.of(Map.of(
                    "role", "user",
                    "content", prompt
            )));
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to generate response from LLM", e);
            return "当前模型服务不可用，请稍后重试。";
        }
    }

    public String generateWithContext(String systemPrompt, String userPrompt) {
        if (isCircuitOpen()) {
            return "当前模型服务不可用，请稍后重试。";
        }
        try {
            return chat(List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
        } catch (Exception e) {
            markUnavailable();
            log.error("Failed to generate response with context", e);
            return "当前模型服务不可用，请稍后重试。";
        }
    }

    private String chat(List<Map<String, String>> messages) throws Exception {
        boolean openAiCompatible = isOpenAiCompatible();
        Map<String, Object> requestPayload = openAiCompatible
                ? Map.of(
                "model", ollamaModel,
                "messages", messages,
                "stream", false,
                "temperature", llmTemperature,
                "max_tokens", llmNumPredict
        )
                : Map.of(
                "model", ollamaModel,
                "messages", messages,
                "stream", false,
                "keep_alive", llmKeepAlive,
                "options", Map.of(
                        "temperature", llmTemperature,
                        "num_predict", llmNumPredict
                )
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(effectiveBaseUrl() + (openAiCompatible ? "/v1/chat/completions" : "/api/chat")))
                .timeout(Duration.ofMillis(llmTimeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestPayload)));
        if (openAiCompatible && apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("LLM chat returned status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = openAiCompatible
                ? root.path("choices").path(0).path("message").path("content").asText("")
                : root.path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("LLM chat response is empty");
        }
        return content;
    }

    private boolean isOpenAiCompatible() {
        return "openai-compatible".equalsIgnoreCase(provider) || "vllm".equalsIgnoreCase(provider);
    }

    private String effectiveBaseUrl() {
        String baseUrl = configuredBaseUrl == null || configuredBaseUrl.isBlank() ? ollamaBaseUrl : configuredBaseUrl;
        return baseUrl.replaceAll("/$", "");
    }

    private boolean isCircuitOpen() {
        return System.currentTimeMillis() < retryAfterEpochMs;
    }

    private void markUnavailable() {
        retryAfterEpochMs = System.currentTimeMillis() + dependencyRetryDelayMs;
    }
}
