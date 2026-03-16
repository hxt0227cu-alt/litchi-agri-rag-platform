package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.EvaluationRecordDto;
import com.litchi.dto.EvaluationStatsResponse;
import com.litchi.dto.PageResponse;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {
    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;

    @Value("${app.evaluation.state-file:data/evaluation-state.json}")
    private String stateFile;

    private final List<EvaluationRecord> records = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    private Path statePath;

    @PostConstruct
    public void init() {
        statePath = resolvePath(stateFile);
        try {
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare evaluation state directory", e);
        }
        loadState();
        if (records.isEmpty()) {
            seedDefaultQuestions();
            persistState();
        }
    }

    public synchronized PageResponse<EvaluationRecordDto> listQuestions(String type, Boolean evaluated, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);

        List<EvaluationRecordDto> filtered = records.stream()
                .filter(record -> normalizedType.isBlank() || record.getType().toLowerCase(Locale.ROOT).contains(normalizedType))
                .filter(record -> evaluated == null || record.isEvaluated() == evaluated)
                .sorted(Comparator.comparing(EvaluationRecord::getId))
                .map(this::toDto)
                .toList();

        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return PageResponse.<EvaluationRecordDto>builder()
                .total(filtered.size())
                .page(safePage)
                .size(safeSize)
                .items(filtered.subList(fromIndex, toIndex))
                .build();
    }

    public synchronized EvaluationRecordDto submitSystemAnswer(long id, String answer) {
        EvaluationRecord record = findRecord(id);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("系统答案不能为空。");
        }

        record.setSystemAnswer(answer.trim());
        record.setBleuScore(computeBleu(record.getReferenceAnswer(), record.getSystemAnswer()));
        record.setEvaluated(record.getHumanScore() != null || record.getSystemAnswer() != null);
        persistState();
        return toDto(record);
    }

    public synchronized EvaluationRecordDto submitHumanScore(long id, Integer humanScore) {
        EvaluationRecord record = findRecord(id);
        if (humanScore == null || humanScore < 1 || humanScore > 5) {
            throw new IllegalArgumentException("人工评分必须在 1 到 5 之间。");
        }

        record.setHumanScore(humanScore);
        record.setEvaluated(record.getSystemAnswer() != null || humanScore != null);
        persistState();
        return toDto(record);
    }

    public synchronized EvaluationStatsResponse getStats() {
        long total = records.size();
        long evaluated = records.stream().filter(EvaluationRecord::isEvaluated).count();
        Double avgBleu = average(records.stream()
                .map(EvaluationRecord::getBleuScore)
                .filter(value -> value != null)
                .toList());
        Double avgHuman = average(records.stream()
                .map(EvaluationRecord::getHumanScore)
                .filter(value -> value != null)
                .map(Integer::doubleValue)
                .toList());

        Map<String, List<EvaluationRecord>> byType = new LinkedHashMap<>();
        for (EvaluationRecord record : records) {
            byType.computeIfAbsent(record.getType(), key -> new ArrayList<>()).add(record);
        }

        List<EvaluationStatsResponse.TypeStat> typeStats = byType.entrySet().stream()
                .map(entry -> EvaluationStatsResponse.TypeStat.builder()
                        .type(entry.getKey())
                        .count(entry.getValue().size())
                        .avgBleuScore(average(entry.getValue().stream()
                                .map(EvaluationRecord::getBleuScore)
                                .filter(value -> value != null)
                                .toList()))
                        .avgHumanScore(average(entry.getValue().stream()
                                .map(EvaluationRecord::getHumanScore)
                                .filter(value -> value != null)
                                .map(Integer::doubleValue)
                                .toList()))
                        .build())
                .toList();

        return EvaluationStatsResponse.builder()
                .total(total)
                .evaluated(evaluated)
                .avgBleuScore(avgBleu)
                .avgHumanScore(avgHuman)
                .byType(typeStats)
                .build();
    }

    private EvaluationRecord findRecord(long id) {
        return records.stream()
                .filter(record -> record.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评测题目不存在。"));
    }

    private EvaluationRecordDto toDto(EvaluationRecord record) {
        return EvaluationRecordDto.builder()
                .id(record.getId())
                .type(record.getType())
                .question(record.getQuestion())
                .referenceAnswer(record.getReferenceAnswer())
                .systemAnswer(record.getSystemAnswer())
                .bleuScore(record.getBleuScore())
                .humanScore(record.getHumanScore())
                .evaluated(record.isEvaluated())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum / values.size() * 1000.0) / 1000.0;
    }

    private double computeBleu(String reference, String candidate) {
        List<String> referenceTokens = tokenize(reference);
        List<String> candidateTokens = tokenize(candidate);
        if (referenceTokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0;
        }

        double precision1 = ngramPrecision(referenceTokens, candidateTokens, 1);
        double precision2 = ngramPrecision(referenceTokens, candidateTokens, 2);
        double precision3 = ngramPrecision(referenceTokens, candidateTokens, 3);
        double precision4 = ngramPrecision(referenceTokens, candidateTokens, 4);
        double brevityPenalty = Math.min(1.0, Math.exp(1.0 - (double) referenceTokens.size() / candidateTokens.size()));
        double score = brevityPenalty * Math.exp(averageLog(List.of(precision1, precision2, precision3, precision4)));
        return Math.round(score * 1000.0) / 1000.0;
    }

    private double averageLog(List<Double> values) {
        double sum = 0;
        for (Double value : values) {
            sum += Math.log(Math.max(value, 1e-9));
        }
        return sum / values.size();
    }

    private double ngramPrecision(List<String> reference, List<String> candidate, int n) {
        List<String> referenceNgrams = ngrams(reference, n);
        List<String> candidateNgrams = ngrams(candidate, n);
        if (referenceNgrams.isEmpty() || candidateNgrams.isEmpty()) {
            return 0.0;
        }

        int hit = 0;
        List<String> pool = new ArrayList<>(referenceNgrams);
        for (String ngram : candidateNgrams) {
            if (pool.remove(ngram)) {
                hit++;
            }
        }
        return (double) hit / candidateNgrams.size();
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.replaceAll("[\\p{Punct}，。；：！？、“”‘’（）()\\r\\n\\t]+", " ")
                        .trim()
                        .split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
    }

    private List<String> ngrams(List<String> tokens, int n) {
        if (tokens.size() < n) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i <= tokens.size() - n; i++) {
            result.add(String.join(" ", tokens.subList(i, i + n)));
        }
        return result;
    }

    private void seedDefaultQuestions() {
        List<SeedTemplate> templates = List.of(
                new SeedTemplate("品种介绍", "桂味荔枝有什么特点？", "桂味属于优质中熟荔枝品种，果香明显，花果期更需要做好营养和通风管理。"),
                new SeedTemplate("种植技术", "荔枝雨季巡园要关注哪些问题？", "重点检查病果、病枝、落果和排水情况，并在雨后及时复查叶面和果面病斑。"),
                new SeedTemplate("病害识别", "霜疫霉病的典型症状是什么？", "常见症状是病果褐斑并伴随白色霉层，在雨季和雾天扩展较快。"),
                new SeedTemplate("虫害防治", "蒂蛀虫高发期应该如何监测？", "花期至幼果期要提高巡园频次，结合诱捕和人工检查尽早发现虫口高峰。"),
                new SeedTemplate("农药使用", "炭疽病发病初期可以考虑哪些药剂？", "可轮换使用咪鲜胺、苯醚甲环唑等药剂，并注意安全间隔期和轮换用药。"),
                new SeedTemplate("综合问题", "连续降雨后荔枝果园管理的优先级是什么？", "先排水和通风，再清理病果病枝，随后根据病虫害风险安排复查和预防性用药。")
        );

        for (int batch = 0; batch < 17; batch++) {
            for (SeedTemplate template : templates) {
                if (records.size() >= 100) {
                    break;
                }
                records.add(EvaluationRecord.builder()
                        .id(idGenerator.getAndIncrement())
                        .type(template.type())
                        .question(template.question() + "（样本 " + (batch + 1) + "）")
                        .referenceAnswer(template.answer())
                        .createdAt(OffsetDateTime.now().minusDays(100L - records.size()).toString())
                        .evaluated(false)
                        .build());
            }
        }
    }

    private void loadState() {
        if (Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), StateSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load evaluation state, starting fresh", e);
                records.clear();
                idGenerator.set(1);
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            return;
        }

        java.util.Optional<MysqlStateStoreService.EvaluationStateData> mysqlState = mysqlStateStoreService.loadEvaluationState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            return;
        }

        if (!records.isEmpty()) {
            mysqlStateStoreService.saveEvaluationState(
                    toMysqlState(new StateSnapshot(new ArrayList<>(records)))
            );
        }
    }

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(records));
        persistLocalState(snapshot);
        mysqlStateStoreService.saveEvaluationState(toMysqlState(snapshot));
    }

    private void persistLocalState() {
        persistLocalState(new StateSnapshot(new ArrayList<>(records)));
    }

    private void persistLocalState(StateSnapshot snapshot) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist evaluation state", e);
        }
    }

    private void applyState(StateSnapshot snapshot) {
        records.clear();
        if (snapshot != null && snapshot.getRecords() != null) {
            records.addAll(snapshot.getRecords());
        }
        long nextId = records.stream().mapToLong(EvaluationRecord::getId).max().orElse(0L) + 1;
        idGenerator.set(nextId);
    }

    private MysqlStateStoreService.EvaluationStateData toMysqlState(StateSnapshot snapshot) {
        List<MysqlStateStoreService.EvaluationRecordData> mysqlRecords = snapshot.getRecords() == null
                ? List.of()
                : snapshot.getRecords().stream()
                .map(record -> new MysqlStateStoreService.EvaluationRecordData(
                        record.getId(),
                        record.getType(),
                        record.getQuestion(),
                        record.getReferenceAnswer(),
                        record.getSystemAnswer(),
                        record.getBleuScore(),
                        record.getHumanScore(),
                        record.isEvaluated(),
                        record.getCreatedAt()
                ))
                .toList();
        return new MysqlStateStoreService.EvaluationStateData(mysqlRecords);
    }

    private StateSnapshot fromMysqlState(MysqlStateStoreService.EvaluationStateData state) {
        List<EvaluationRecord> mysqlRecords = state.getRecords() == null
                ? List.of()
                : state.getRecords().stream()
                .map(record -> EvaluationRecord.builder()
                        .id(record.getId())
                        .type(record.getType())
                        .question(record.getQuestion())
                        .referenceAnswer(record.getReferenceAnswer())
                        .systemAnswer(record.getSystemAnswer())
                        .bleuScore(record.getBleuScore())
                        .humanScore(record.getHumanScore())
                        .evaluated(record.isEvaluated())
                        .createdAt(record.getCreatedAt())
                        .build())
                .toList();
        return new StateSnapshot(new ArrayList<>(mysqlRecords));
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path applicationDir = new ApplicationHome(EvaluationService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    private record SeedTemplate(String type, String question, String answer) {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class EvaluationRecord {
        private long id;
        private String type;
        private String question;
        private String referenceAnswer;
        private String systemAnswer;
        private Double bleuScore;
        private Integer humanScore;
        private boolean evaluated;
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<EvaluationRecord> records = new ArrayList<>();
    }
}
