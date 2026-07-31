package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.FeedbackRecordDto;
import com.litchi.dto.FeedbackStatsResponse;
import com.litchi.dto.SubmitFeedbackRequest;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;

    @Value("${app.feedback.state-file:data/feedback-state.json}")
    private String stateFile;

    private final List<FeedbackRecord> records = new ArrayList<>();
    private Path statePath;

    @PostConstruct
    public void init() {
        statePath = resolvePath(stateFile);
        try {
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare feedback state directory", e);
        }
        loadState();
    }

    public synchronized FeedbackRecordDto submit(AuthenticatedUser user, SubmitFeedbackRequest request) {
        if (user == null) {
            throw new IllegalArgumentException("当前反馈提交缺少用户信息。");
        }
        if (request == null) {
            throw new IllegalArgumentException("反馈内容不能为空。");
        }

        String module = normalizeModule(request.getModule());
        validateScore(request.getOverallScore(), "总体满意度");
        validateScore(request.getAccuracyScore(), "准确性");
        validateScore(request.getPracticalityScore(), "实用性");
        validateScore(request.getFluencyScore(), "流畅性");

        FeedbackRecord record = FeedbackRecord.builder()
                .id(UUID.randomUUID().toString())
                .userId(user.id())
                .username(user.username())
                .role(user.role())
                .module(module)
                .overallScore(request.getOverallScore())
                .accuracyScore(request.getAccuracyScore())
                .practicalityScore(request.getPracticalityScore())
                .fluencyScore(request.getFluencyScore())
                .comment(trimComment(request.getComment()))
                .createdAt(OffsetDateTime.now().toString())
                .build();

        records.add(record);
        persistState();
        log.info("Feedback submitted id={} by user={}", record.getId(), user.username());
        return toDto(record);
    }

    public synchronized FeedbackStatsResponse getStats() {
        return FeedbackStatsResponse.builder()
                .total(records.size())
                .avgOverallScore(average(records.stream().map(FeedbackRecord::getOverallScore).toList()))
                .avgAccuracyScore(average(records.stream().map(FeedbackRecord::getAccuracyScore).toList()))
                .avgPracticalityScore(average(records.stream().map(FeedbackRecord::getPracticalityScore).toList()))
                .avgFluencyScore(average(records.stream().map(FeedbackRecord::getFluencyScore).toList()))
                .byModule(buildModuleStats())
                .recent(records.stream()
                        .sorted(Comparator.comparing(FeedbackRecord::getCreatedAt).reversed())
                        .limit(8)
                        .map(this::toDto)
                        .toList())
                .build();
    }

    private List<FeedbackStatsResponse.ModuleStat> buildModuleStats() {
        return records.stream()
                .map(FeedbackRecord::getModule)
                .distinct()
                .sorted()
                .map(module -> {
                    List<FeedbackRecord> items = records.stream()
                            .filter(record -> module.equals(record.getModule()))
                            .toList();
                    return FeedbackStatsResponse.ModuleStat.builder()
                            .module(module)
                            .count(items.size())
                            .avgOverallScore(average(items.stream().map(FeedbackRecord::getOverallScore).toList()))
                            .build();
                })
                .toList();
    }

    private void validateScore(Integer score, String label) {
        if (score == null || score < 1 || score > 5) {
            throw new IllegalArgumentException(label + "必须为 1 到 5 分。");
        }
    }

    private String normalizeModule(String module) {
        String value = module == null ? "" : module.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("反馈模块不能为空。");
        }
        return value;
    }

    private String trimComment(String comment) {
        if (comment == null) {
            return "";
        }
        String value = comment.trim();
        if (value.length() > 500) {
            throw new IllegalArgumentException("反馈备注不能超过 500 个字符。");
        }
        return value;
    }

    private Double average(List<Integer> values) {
        return values.isEmpty()
                ? null
                : Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(0) * 100.0) / 100.0;
    }

    private FeedbackRecordDto toDto(FeedbackRecord record) {
        return FeedbackRecordDto.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .username(record.getUsername())
                .role(record.getRole())
                .module(record.getModule())
                .overallScore(record.getOverallScore())
                .accuracyScore(record.getAccuracyScore())
                .practicalityScore(record.getPracticalityScore())
                .fluencyScore(record.getFluencyScore())
                .comment(record.getComment())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private void loadState() {
        if (statePath != null && Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), StateSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load feedback state from {}", statePath, e);
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            return;
        }

        java.util.Optional<MysqlStateStoreService.FeedbackStateData> mysqlState = mysqlStateStoreService.loadFeedbackState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            return;
        }

        if (!records.isEmpty()) {
            mysqlStateStoreService.saveFeedbackState(toMysqlState(new StateSnapshot(new ArrayList<>(records))));
        }
    }

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(records));
        persistLocalState(snapshot);
        mysqlStateStoreService.saveFeedbackState(toMysqlState(snapshot));
    }

    private void persistLocalState() {
        persistLocalState(new StateSnapshot(new ArrayList<>(records)));
    }

    private void persistLocalState(StateSnapshot snapshot) {
        if (statePath == null) {
            return;
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist feedback state", e);
        }
    }

    private void applyState(StateSnapshot snapshot) {
        records.clear();
        if (snapshot != null && snapshot.getRecords() != null) {
            records.addAll(snapshot.getRecords());
        }
    }

    private StateSnapshot fromMysqlState(MysqlStateStoreService.FeedbackStateData state) {
        List<FeedbackRecord> mysqlRecords = state.getRecords() == null
                ? List.of()
                : state.getRecords().stream()
                .map(record -> FeedbackRecord.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                        .username(record.getUsername())
                        .role(record.getRole())
                        .module(record.getModule())
                        .overallScore(record.getOverallScore())
                        .accuracyScore(record.getAccuracyScore())
                        .practicalityScore(record.getPracticalityScore())
                        .fluencyScore(record.getFluencyScore())
                        .comment(record.getComment())
                        .createdAt(record.getCreatedAt())
                        .build())
                .toList();
        return new StateSnapshot(mysqlRecords);
    }

    private MysqlStateStoreService.FeedbackStateData toMysqlState(StateSnapshot snapshot) {
        List<MysqlStateStoreService.FeedbackRecordData> mysqlRecords = snapshot.getRecords() == null
                ? List.of()
                : snapshot.getRecords().stream()
                .map(record -> new MysqlStateStoreService.FeedbackRecordData(
                        record.getId(),
                        record.getUserId(),
                        record.getUsername(),
                        record.getRole(),
                        record.getModule(),
                        record.getOverallScore(),
                        record.getAccuracyScore(),
                        record.getPracticalityScore(),
                        record.getFluencyScore(),
                        record.getComment(),
                        record.getCreatedAt()
                ))
                .toList();
        return new MysqlStateStoreService.FeedbackStateData(mysqlRecords);
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path applicationDir = new ApplicationHome(FeedbackService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        @Builder.Default
        private List<FeedbackRecord> records = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class FeedbackRecord {
        private String id;
        private String userId;
        private String username;
        private String role;
        private String module;
        private Integer overallScore;
        private Integer accuracyScore;
        private Integer practicalityScore;
        private Integer fluencyScore;
        private String comment;
        private String createdAt;
    }
}
