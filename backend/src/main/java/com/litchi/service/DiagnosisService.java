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
        String disease = lowered.contains("tanju") || lowered.contains("anthracnose") ? "炭疽病" : "霜疫霉病";

        List<DiagnosisResult.DiseaseInfo> diseases = "炭疽病".equals(disease)
                ? List.of(
                DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.68")).build(),
                DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.22")).build()
        )
                : List.of(
                DiagnosisResult.DiseaseInfo.builder().name("霜疫霉病").confidence(new BigDecimal("0.71")).build(),
                DiagnosisResult.DiseaseInfo.builder().name("炭疽病").confidence(new BigDecimal("0.19")).build()
        );

        return DiagnosisResult.builder()
                .disease(diseases.get(0).getName())
                .confidence(diseases.get(0).getConfidence())
                .suggestions(generateSuggestions(diseases.get(0).getName()))
                .diseases(diseases)
                .engine("backend-fallback")
                .demoMode(true)
                .note("独立识病服务不可用，当前结果来自后端兜底规则。")
                .build();
    }

    private List<String> generateSuggestions(String diseaseName) {
        return switch (diseaseName) {
            case "霜疫霉病" -> List.of(
                    "加强果园通风透光，及时清理病叶病果。",
                    "发病初期可喷施烯酰吗啉或霜霉威盐酸盐。",
                    "雨季来临前提前预防，每 7 到 10 天复查一次。"
            );
            case "炭疽病" -> List.of(
                    "冬季清园并剪除病枝病叶，降低越冬病源。",
                    "可结合咪鲜胺或苯醚甲环唑进行防治。",
                    "果实发育期重点巡查，避免高温高湿环境持续过久。"
            );
            default -> List.of(
                    "建议结合田间症状做进一步人工复核。",
                    "如症状持续扩散，请咨询专业农技人员制定防治方案。"
            );
        };
    }

    private record MultipartPayload(String boundary, byte[] body) {
    }
}
