package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.DiagnosisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final ObjectMapper objectMapper;

    @Value("${app.diagnosis.service-url:http://localhost:8090/predict}")
    private String diagnosisServiceUrl;

    @Value("${app.diagnosis.timeout-ms:5000}")
    private int timeoutMs;

    public boolean isAvailable() {
        return getHealth().reachable();
    }

    public HealthStatus getHealth() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveHealthUrl()))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return HealthStatus.unavailable();
            }

            return parseHealthStatus(response.body());
        } catch (Exception e) {
            log.debug("Diagnosis service availability check failed", e);
            return HealthStatus.unavailable();
        }
    }

    public DiagnosisResult diagnose(MultipartFile image) {
        log.info("Processing diagnosis image: {}", image.getOriginalFilename());

        try {
            DiagnosisResult result = callInferenceService(image);
            if (result != null && result.getDisease() != null) {
                return result;
            }
        } catch (Exception e) {
            log.warn("Diagnosis inference service unavailable, using backend fallback", e);
        }

        return buildFallbackResult(image.getOriginalFilename());
    }

    private DiagnosisResult callInferenceService(MultipartFile image) throws IOException, InterruptedException {
        MultipartPayload payload = buildMultipartPayload(image);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(diagnosisServiceUrl))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "multipart/form-data; boundary=" + payload.boundary())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.body()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Diagnosis service returned status " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), DiagnosisResult.class);
    }

    private MultipartPayload buildMultipartPayload(MultipartFile image) throws IOException {
        String boundary = "----LitchiDiagnosis" + UUID.randomUUID().toString().replace("-", "");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + sanitizeFileName(image.getOriginalFilename()) + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + resolveContentType(image) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(image.getBytes());
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return new MultipartPayload(boundary, output.toByteArray());
        }
    }

    private String resolveHealthUrl() {
        URI uri = URI.create(diagnosisServiceUrl);
        String path = uri.getPath();
        String healthPath = path == null || path.isBlank()
                ? "/health"
                : path.replaceFirst("/predict/?$", "/health");
        URI healthUri = URI.create(String.format("%s://%s%s%s",
                uri.getScheme(),
                uri.getAuthority(),
                healthPath.startsWith("/") ? "" : "/",
                healthPath));
        return healthUri.toString();
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "diagnosis-image.jpg";
        }
        return originalFilename.replaceAll("[\\\\/:*?\"<>|]+", "_");
    }

    private String resolveContentType(MultipartFile image) {
        if (image.getContentType() != null && !image.getContentType().isBlank()) {
            return image.getContentType();
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private DiagnosisResult buildFallbackResult(String fileName) {
        String lowered = fileName == null ? "" : fileName.toLowerCase();
        String disease = lowered.contains("healthy")
                ? "健康叶片"
                : lowered.contains("anthracnose") || lowered.contains("tanju")
                ? "炭疽病"
                : lowered.contains("blight") || lowered.contains("mildew") || lowered.contains("downy")
                ? "霜疫霉病"
                : "疑似病害";

        List<DiagnosisResult.DiseaseInfo> diseases = switch (disease) {
            case "健康叶片" -> List.of(
                    DiagnosisResult.DiseaseInfo.builder().name("健康叶片").confidence(new BigDecimal("0.82")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.10")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.08")).build()
            );
            case "炭疽病" -> List.of(
                    DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.68")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.22")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("健康叶片").confidence(new BigDecimal("0.10")).build()
            );
            case "霜疫霉病" -> List.of(
                    DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.71")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.19")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("健康叶片").confidence(new BigDecimal("0.10")).build()
            );
            default -> List.of(
                    DiagnosisResult.DiseaseInfo.builder().name("疑似病害").confidence(new BigDecimal("0.55")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.25")).build(),
                    DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.20")).build()
            );
        };

        return DiagnosisResult.builder()
                .disease(diseases.get(0).getName())
                .confidence(diseases.get(0).getConfidence())
                .suggestions(generateSuggestions(diseases.get(0).getName()))
                .diseases(diseases)
                .engine("backend-fallback")
                .demoMode(true)
                .note("独立识别服务不可用，当前结果来自后端兜底规则。")
                .build();
    }

    private List<String> generateSuggestions(String diseaseName) {
        return switch (diseaseName) {
            case "健康叶片" -> List.of(
                    "当前叶片状态较稳定，可继续保持通风透光和常规巡园。",
                    "花果期注意雨后复查，重点观察是否出现新病斑或虫孔。",
                    "答辩展示时可说明系统也支持健康样本的基础识别。"
            );
            case "霜疫霉病" -> List.of(
                    "加强果园排水和通风，及时清理病果病枝。",
                    "发病初期可结合烯酰吗啉等药剂开展防治，并注意轮换用药。",
                    "雨季前后提高巡园频次，重点检查花穗和幼果。"
            );
            case "炭疽病" -> List.of(
                    "冬季清园并剪除病枝病叶，降低越冬病源。",
                    "可轮换使用咪鲜胺或苯醚甲环唑等药剂开展防治。",
                    "果实发育期重点巡查，避免高温高湿环境持续过久。"
            );
            default -> List.of(
                    "建议结合田间症状进一步人工复核。",
                    "如症状持续扩散，请咨询农技人员制定针对性防治方案。",
                    "演示时可说明该模式用于缺少模型时的兜底识别。"
            );
        };
    }

    private HealthStatus parseHealthStatus(String body) {
        try {
            var root = objectMapper.readTree(body);
            boolean demoMode = root.path("demoMode").asBoolean(true);
            boolean modelLoaded = root.path("modelLoaded").asBoolean(false);
            String engine = root.path("engine").asText(modelLoaded ? "ultralytics-yolo" : "demo-rule");
            String systemStatus = root.path("status").asText(modelLoaded ? "connected" : "degraded");

            if (modelLoaded && !demoMode) {
                return new HealthStatus(true, false, true, engine, "connected");
            }

            return new HealthStatus(true, demoMode, false, engine, systemStatus);
        } catch (Exception e) {
            log.debug("Failed to parse diagnosis service health payload", e);
            return new HealthStatus(true, true, false, "unknown", "degraded");
        }
    }

    private record MultipartPayload(String boundary, byte[] body) {
    }

    public record HealthStatus(
            boolean reachable,
            boolean demoMode,
            boolean modelLoaded,
            String engine,
            String systemStatus
    ) {
        public static HealthStatus unavailable() {
            return new HealthStatus(false, true, false, "unavailable", "unavailable");
        }
    }
}
