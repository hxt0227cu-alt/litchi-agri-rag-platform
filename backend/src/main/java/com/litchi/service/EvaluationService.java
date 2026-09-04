package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatHistoryItem;
import com.litchi.dto.EvaluationRecordDto;
import com.litchi.dto.EvaluationRubricScore;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final double AUTO_REVIEW_THRESHOLD = 72.0;
    private static final int MAX_ACTIVE_FEEDBACK_RULES = 4;
    private static final int MANUAL_FEEDBACK_WEIGHT = 5;
    private static final int AUTO_FEEDBACK_WEIGHT = 1;
    /** 自动反馈规则 TTL：chat 热路径避免每次全量重算全部历史聊天记录。 */
    private static final long FEEDBACK_RULES_TTL_MS = 30_000L;
    private static final String RULE_SAFETY = "safety";
    private static final String RULE_KNOWLEDGE = "knowledge";
    private static final String RULE_ACCURACY = "accuracy";
    private static final String RULE_STRUCTURE = "structure";
    private static final List<String> DOMAIN_KEYWORDS = List.of(
            "荔枝", "病害", "病虫害", "炭疽病", "霜疫霉病", "蒂蛀虫", "荔枝蝽",
            "花穗", "幼果", "果面", "叶片", "病斑", "白色霉层", "雨季", "排水", "通风", "清园",
            "修剪", "轮换用药", "安全间隔期", "监测", "复查", "拍照", "巡园", "落果", "虫果"
    );
    private static final List<String> SAFETY_KEYWORDS = List.of(
            "安全间隔期", "标签", "登记", "风险", "注意", "避免", "不要", "复查", "拍照复核", "不足以判断"
    );
    private static final List<String> ACTION_KEYWORDS = List.of(
            "建议", "先", "再", "及时", "优先", "处理", "监测", "复查", "拍照", "提交求助", "查看方案",
            "补充", "记录", "判断", "查看", "最好", "缩小范围"
    );
    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "病斑", "白色霉层", "落果", "虫孔", "腐烂", "花穗", "果梗", "叶片", "果面"
    );
    private static final List<String> DISEASE_NAMES = List.of(
            "炭疽病", "霜疫霉病", "蒂蛀虫", "荔枝蝽"
    );
    private static final List<String> PESTICIDE_KEYWORDS = List.of(
            "药剂", "用药", "倍", "喷施", "喷药", "杀菌剂", "杀虫剂", "咪鲜胺", "苯醚甲环唑", "烯酰吗啉"
    );
    private static final List<String> NOTE_SAFETY_TRIGGERS = List.of("安全", "药", "倍", "风险", "间隔期");
    private static final List<String> NOTE_KNOWLEDGE_TRIGGERS = List.of("知识库", "资料", "证据", "来源", "文档", "补充");

    private volatile List<EvaluationStatsResponse.ActiveFeedbackRule> cachedActiveFeedbackRules = List.of();
    private volatile long feedbackRulesCacheEpochMs;
    private static final List<String> NOTE_ACCURACY_TRIGGERS = List.of("检索", "提示词", "核心", "反问", "跑题", "不准");
    private static final List<String> NOTE_STRUCTURE_TRIGGERS = List.of("结构", "完整", "可执行", "下一步", "三段", "四段");

    private final ObjectMapper objectMapper;
    private final ChatHistoryService chatHistoryService;

    @Value("${app.evaluation.state-file:data/evaluation-state.json}")
    private String stateFile;

    private final Map<String, ReviewState> reviewStateMap = new LinkedHashMap<>();
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
        // 后台周期预热反馈规则缓存：chat 热路径永远读缓存，不再在请求线程里做全量评分重算。
        // TTL 期内由调度线程每 TTL/2 刷新一次，避免并发下 TTL 过期瞬间所有请求同时触发全量重建。
        java.util.concurrent.ScheduledExecutorService refresher =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "eval-feedback-refresher");
                    thread.setDaemon(true);
                    return thread;
                });
        refresher.scheduleAtFixedRate(
                this::refreshFeedbackRulesCache,
                FEEDBACK_RULES_TTL_MS / 2,
                FEEDBACK_RULES_TTL_MS / 2,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 后台线程专用：全量重算反馈规则并写回缓存。失败时保留旧缓存（不中断线上请求）。
     */
    private synchronized void refreshFeedbackRulesCache() {
        try {
            List<EvaluationStatsResponse.ActiveFeedbackRule> computed =
                    buildActiveFeedbackRules(buildEvaluationRecords());
            cachedActiveFeedbackRules = computed;
            feedbackRulesCacheEpochMs = System.currentTimeMillis();
            log.debug("Evaluation feedback rules cache refreshed: {} rules, {} records",
                    computed.size(), chatHistoryService.getAllHistoryForEvaluation().size());
        } catch (Exception e) {
            log.warn("Failed to refresh evaluation feedback rules cache, keeping previous cache", e);
        }
    }

    public synchronized PageResponse<EvaluationRecordDto> listQuestions(String type, Boolean evaluated, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);

        List<EvaluationRecordDto> filtered = buildEvaluationRecords().stream()
                .filter(record -> normalizedType.isBlank() || record.getType().toLowerCase(Locale.ROOT).contains(normalizedType))
                .filter(record -> evaluated == null || record.isEvaluated() == evaluated)
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

    public synchronized EvaluationRecordDto submitSystemAnswer(String id, String answer) {
        ChatHistoryItem chatRecord = findChatRecord(id);
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("系统答案不能为空。");
        }
        return buildEvaluationRecord(chatRecord, answer.trim());
    }

    public synchronized EvaluationRecordDto submitHumanScore(String id, Integer humanScore, String reviewNote) {
        if (humanScore == null || humanScore < 1 || humanScore > 5) {
            throw new IllegalArgumentException("人工评分必须在 1 到 5 之间。");
        }

        ChatHistoryItem chatRecord = findChatRecord(id);
        reviewStateMap.put(id, ReviewState.builder()
                .id(id)
                .humanScore(humanScore)
                .reviewNote(trimToNull(reviewNote))
                .reviewStatus("reviewed")
                .reviewedAt(OffsetDateTime.now().toString())
                .build());
        persistState();
        return buildEvaluationRecord(chatRecord, chatRecord.getAnswer());
    }

    public synchronized EvaluationStatsResponse getStats() {
        List<EvaluationRecordDto> records = buildEvaluationRecords();

        long total = records.size();
        long evaluated = records.stream().filter(EvaluationRecordDto::isEvaluated).count();
        long reviewed = records.stream().filter(record -> record.getHumanScore() != null).count();
        long reviewPending = records.stream()
                .filter(record -> "pending".equals(record.getReviewStatus()))
                .count();
        long lowScoreCount = records.stream()
                .filter(record -> record.getAutoScore() != null && record.getAutoScore() < AUTO_REVIEW_THRESHOLD)
                .count();

        Double avgAuto = average(records.stream()
                .map(EvaluationRecordDto::getAutoScore)
                .filter(value -> value != null)
                .toList());
        Double avgHuman = average(records.stream()
                .map(EvaluationRecordDto::getHumanScore)
                .filter(value -> value != null)
                .map(Integer::doubleValue)
                .toList());
        Double avgBleu = average(records.stream()
                .map(EvaluationRecordDto::getBleuScore)
                .filter(value -> value != null)
                .toList());

        OffsetDateTime now = OffsetDateTime.now();
        Double recentAvg = average(records.stream()
                .filter(record -> isWithinDays(record.getCreatedAt(), now, 7))
                .map(EvaluationRecordDto::getAutoScore)
                .filter(value -> value != null)
                .toList());
        Double previousAvg = average(records.stream()
                .filter(record -> isWithinDays(record.getCreatedAt(), now.minusDays(7), 7))
                .map(EvaluationRecordDto::getAutoScore)
                .filter(value -> value != null)
                .toList());
        Double trendDelta = recentAvg == null || previousAvg == null
                ? null
                : Math.round((recentAvg - previousAvg) * 100.0) / 100.0;

        Map<String, List<EvaluationRecordDto>> byType = new LinkedHashMap<>();
        for (EvaluationRecordDto record : records) {
            byType.computeIfAbsent(record.getType(), key -> new ArrayList<>()).add(record);
        }

        List<EvaluationStatsResponse.TypeStat> typeStats = byType.entrySet().stream()
                .map(entry -> EvaluationStatsResponse.TypeStat.builder()
                        .type(entry.getKey())
                        .count(entry.getValue().size())
                        .avgAutoScore(average(entry.getValue().stream()
                                .map(EvaluationRecordDto::getAutoScore)
                                .filter(value -> value != null)
                                .toList()))
                        .avgBleuScore(average(entry.getValue().stream()
                                .map(EvaluationRecordDto::getBleuScore)
                                .filter(value -> value != null)
                                .toList()))
                        .avgHumanScore(average(entry.getValue().stream()
                                .map(EvaluationRecordDto::getHumanScore)
                                .filter(value -> value != null)
                                .map(Integer::doubleValue)
                                .toList()))
                        .build())
                .sorted(Comparator.comparing(EvaluationStatsResponse.TypeStat::getCount).reversed())
                .toList();

        return EvaluationStatsResponse.builder()
                .total(total)
                .evaluated(evaluated)
                .avgAutoScore(avgAuto)
                .avgBleuScore(avgBleu)
                .avgHumanScore(avgHuman)
                .reviewed(reviewed)
                .reviewPending(reviewPending)
                .lowScoreCount(lowScoreCount)
                .recentAvgAutoScore(recentAvg)
                .previousAvgAutoScore(previousAvg)
                .scoreTrendDelta(trendDelta)
                .byType(typeStats)
                .activeFeedbackRules(buildActiveFeedbackRules(records))
                .build();
    }

    public List<EvaluationStatsResponse.ActiveFeedbackRule> getActiveFeedbackRules() {
        // 热路径纯缓存读：正常情况缓存由后台刷新线程维护。
        List<EvaluationStatsResponse.ActiveFeedbackRule> cached = cachedActiveFeedbackRules;
        if (cached != null && System.currentTimeMillis() - feedbackRulesCacheEpochMs < FEEDBACK_RULES_TTL_MS * 3) {
            return cached;
        }
        // 兜底：调度线程尚未首刷（或长期失败）时，由单个请求在锁内补齐，避免并发重算风暴。
        synchronized (this) {
            cached = cachedActiveFeedbackRules;
            if (cached != null && System.currentTimeMillis() - feedbackRulesCacheEpochMs < FEEDBACK_RULES_TTL_MS * 3) {
                return cached;
            }
            List<EvaluationStatsResponse.ActiveFeedbackRule> computed =
                    buildActiveFeedbackRules(buildEvaluationRecords());
            cachedActiveFeedbackRules = computed;
            feedbackRulesCacheEpochMs = System.currentTimeMillis();
            return computed;
        }
    }

    private List<EvaluationRecordDto> buildEvaluationRecords() {
        return chatHistoryService.getAllHistoryForEvaluation().stream()
                .filter(this::isEvaluable)
                .map(chatRecord -> buildEvaluationRecord(chatRecord, chatRecord.getAnswer()))
                .sorted(Comparator.comparing(EvaluationRecordDto::getCreatedAt).reversed())
                .toList();
    }

    private EvaluationRecordDto buildEvaluationRecord(ChatHistoryItem chatRecord, String answer) {
        ReviewState reviewState = reviewStateMap.get(chatRecord.getId());
        EvaluationRubricScore scoreBreakdown = computeRubricScore(chatRecord.getQuestion(), answer, chatRecord.getSources());
        Double autoScore = calculateAutoScore(scoreBreakdown);
        String reviewStatus = resolveReviewStatus(autoScore, reviewState);
        String type = classifyQuestion(chatRecord.getQuestion(), chatRecord.getSources());
        String suggestedAction = buildSuggestedAction(scoreBreakdown, chatRecord.getSources());

        return EvaluationRecordDto.builder()
                .id(chatRecord.getId())
                .sessionId(chatRecord.getSessionId())
                .type(type)
                .question(chatRecord.getQuestion())
                .referenceAnswer(null)
                .systemAnswer(answer)
                .autoScore(autoScore)
                .scoreBreakdown(scoreBreakdown)
                .bleuScore(sourceSupportScore(answer, chatRecord.getSources()))
                .humanScore(reviewState == null ? null : reviewState.getHumanScore())
                .reviewNote(reviewState == null ? null : reviewState.getReviewNote())
                .reviewStatus(reviewStatus)
                .sourceCount(chatRecord.getSources() == null ? 0 : chatRecord.getSources().size())
                .suggestedAction(suggestedAction)
                .improvementHint(buildImprovementHint(scoreBreakdown, suggestedAction))
                .sources(chatRecord.getSources() == null ? List.of() : chatRecord.getSources())
                .evaluated(answer != null && !answer.isBlank())
                .createdAt(chatRecord.getCreatedAt())
                .build();
    }

    private boolean isEvaluable(ChatHistoryItem item) {
        return item != null
                && item.getAnswer() != null
                && !item.getAnswer().isBlank()
                && isReadableQuestion(item.getQuestion());
    }

    private boolean isReadableQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String trimmed = question.trim();
        long placeholderMarks = trimmed.chars().filter(ch -> ch == '?').count();
        long readableChars = trimmed.codePoints()
                .filter(codePoint -> Character.isLetterOrDigit(codePoint) || isCjk(codePoint))
                .count();
        return !(placeholderMarks >= Math.max(4, trimmed.length() / 2) && readableChars <= 2);
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private ChatHistoryItem findChatRecord(String id) {
        return chatHistoryService.getAllHistoryForEvaluation().stream()
                .filter(this::isEvaluable)
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评测记录不存在。"));
    }

    private EvaluationRubricScore computeRubricScore(String question, String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        return EvaluationRubricScore.builder()
                .accuracyScore(scoreAccuracy(question, answer, sources))
                .safetyScore(scoreSafety(answer, sources))
                .completenessScore(scoreCompleteness(question, answer))
                .actionabilityScore(scoreActionability(question, answer))
                .build();
    }

    private Double calculateAutoScore(EvaluationRubricScore score) {
        if (score == null) {
            return null;
        }
        double total = safeInteger(score.getAccuracyScore())
                + safeInteger(score.getSafetyScore())
                + safeInteger(score.getCompletenessScore())
                + safeInteger(score.getActionabilityScore());
        return Math.round(total * 10.0) / 10.0;
    }

    private int scoreAccuracy(String question, String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        List<String> questionKeywords = extractQuestionKeywords(question);
        double keywordCoverage = questionKeywords.isEmpty()
                ? lexicalSimilarity(question, answer)
                : keywordCoverage(questionKeywords, answer);
        double sourceSupport = sourceSupportScore(answer, sources) / 25.0;
        double overviewBonus = isDiseaseOverviewQuestion(question) && countKeywordHits(answer, DISEASE_NAMES) >= 2 ? 0.2 : 0.0;
        double questionPenalty = looksLikeFollowUpQuestion(answer) ? 0.35 : 0.0;
        double score = 5 + keywordCoverage * 10 + sourceSupport * 7 + overviewBonus * 5 - questionPenalty * 20;
        return clampScore(score);
    }

    private int scoreSafety(String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        double sourceSupport = sourceSupportScore(answer, sources) / 25.0;
        boolean unsupportedDosage = containsUnsupportedDosage(answer, sources);
        boolean unsupportedAction = containsUnsupportedAction(answer, sources);
        boolean hasRiskCue = containsAny(answer, SAFETY_KEYWORDS);
        boolean mentionsPesticide = containsAny(answer, PESTICIDE_KEYWORDS);
        boolean lowRiskAnswer = !mentionsPesticide && !unsupportedDosage && !unsupportedAction;

        double score = lowRiskAnswer ? 14 + sourceSupport * 7 : 8 + sourceSupport * 8;
        score += hasRiskCue ? 4 : (lowRiskAnswer ? 3 : 0);
        if (unsupportedDosage) {
            score -= 10;
        }
        if (unsupportedAction) {
            score -= 7;
        }
        return clampScore(score);
    }

    private int scoreCompleteness(String question, String answer) {
        int buckets = 0;
        if (containsAny(answer, DISEASE_NAMES) || containsAny(answer, SYMPTOM_KEYWORDS)) {
            buckets++;
        }
        if (containsAny(answer, ACTION_KEYWORDS)) {
            buckets++;
        }
        if (containsAny(answer, SAFETY_KEYWORDS)) {
            buckets++;
        }
        if (isDiseaseOverviewQuestion(question) && countKeywordHits(answer, DISEASE_NAMES) >= 2) {
            buckets++;
        }
        double lengthBonus = Math.min(normalizeText(answer).length() / 55.0, 4.0);
        return clampScore(4 + buckets * 4 + lengthBonus);
    }

    private int scoreActionability(String question, String answer) {
        int actionHits = countKeywordHits(answer, ACTION_KEYWORDS);
        int orderedSteps = countKeywordHits(answer, List.of("先", "再", "然后", "最后"));
        boolean hasNextStep = containsAny(answer, List.of("继续提问", "查看解决方案", "提交求助", "拍照复核", "巡园复查"));
        boolean overviewGuidance = isDiseaseOverviewQuestion(question) && containsAny(answer, List.of("如果", "可以先", "建议补充"));
        double score = 5 + Math.min(actionHits, 4) * 3 + Math.min(orderedSteps, 3) * 2 + (hasNextStep ? 3 : 0) + (overviewGuidance ? 3 : 0);
        return clampScore(score);
    }

    private Double sourceSupportScore(String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        String sourceText = collectSourceText(sources);
        if (sourceText.isBlank() || answer == null || answer.isBlank()) {
            return 0.0;
        }
        double similarity = lexicalSimilarity(sourceText, answer);
        return Math.round(Math.min(25.0, 5 + similarity * 20) * 10.0) / 10.0;
    }

    private List<String> extractQuestionKeywords(String question) {
        String normalized = normalizeText(question);
        LinkedHashSet<String> keywords = DOMAIN_KEYWORDS.stream()
                .map(this::normalizeText)
                .filter(keyword -> !keyword.isBlank() && normalized.contains(keyword))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (keywords.isEmpty()) {
            keywords.addAll(Arrays.stream(normalized.split("\\s+"))
                    .filter(token -> token.length() >= 2)
                    .limit(6)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return new ArrayList<>(keywords);
    }

    private String classifyQuestion(String question, List<com.litchi.dto.ChatResponse.Source> sources) {
        String text = question == null ? "" : question;
        if (isDiseaseOverviewQuestion(text)) {
            return "病害总览";
        }
        if (text.contains("区别") || text.contains("区分") || text.contains("分辨")) {
            return "病害区分";
        }
        if (text.contains("监测") || text.contains("识别") || text.contains("判断")) {
            return "识别监测";
        }
        if (text.contains("花果期") || text.contains("花期") || text.contains("果期") || text.contains("管理")) {
            return "花果期管理";
        }
        if (text.contains("防治") || text.contains("处理") || text.contains("怎么办")) {
            return "防治建议";
        }
        if (sources != null && sources.stream().anyMatch(source -> safeText(source.getTitle()).contains("虫"))) {
            return "虫害问答";
        }
        return "综合问答";
    }

    private String buildSuggestedAction(EvaluationRubricScore score, List<com.litchi.dto.ChatResponse.Source> sources) {
        int sourceCount = sources == null ? 0 : sources.size();
        if (score == null) {
            return "人工复核";
        }
        if (safeInteger(score.getSafetyScore()) <= 10) {
            return "补安全规则";
        }
        if (safeInteger(score.getAccuracyScore()) <= 13 && sourceCount <= 1) {
            return "补知识库";
        }
        if (safeInteger(score.getAccuracyScore()) <= 13) {
            return "调检索与提示词";
        }
        if (safeInteger(score.getCompletenessScore()) <= 14 || safeInteger(score.getActionabilityScore()) <= 14) {
            return "优化回答结构";
        }
        return "持续观察";
    }

    private String buildImprovementHint(EvaluationRubricScore score, String suggestedAction) {
        if (score == null) {
            return "这条记录建议先做人工复核。";
        }
        return switch (suggestedAction) {
            case "补知识库" -> "这条回答更像是证据不够，不是单纯表述问题，优先补充同主题权威资料。";
            case "调检索与提示词" -> "资料已有一定覆盖，但回答没有答到问题核心，优先调检索排序和提示词约束。";
            case "补安全规则" -> "这条回答存在越界建议或风险提示不足，优先补安全边界规则。";
            case "优化回答结构" -> "建议把回答改成“判断依据 + 建议动作 + 风险提醒”三段式，让农户更容易执行。";
            default -> "当前自动评分已基本稳定，后续继续观察同类问题即可。";
        };
    }

    private List<EvaluationStatsResponse.ActiveFeedbackRule> buildActiveFeedbackRules(List<EvaluationRecordDto> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<String, FeedbackRuleBucket> buckets = new LinkedHashMap<>();
        for (EvaluationRecordDto record : records) {
            boolean hasManualSignal = hasManualFeedbackSignal(record);
            boolean hasAutoSignal = isAutoFeedbackCandidate(record);
            if (!hasManualSignal && !hasAutoSignal) {
                continue;
            }

            String sourceType = hasManualSignal ? "human_review" : "auto_score";
            int weight = hasManualSignal ? MANUAL_FEEDBACK_WEIGHT : AUTO_FEEDBACK_WEIGHT;
            for (String category : resolveFeedbackCategories(record, hasManualSignal)) {
                buckets.computeIfAbsent(category, this::newFeedbackRuleBucket)
                        .addEvidence(sourceType, weight);
            }
        }

        return buckets.values().stream()
                .sorted(Comparator.comparingInt(FeedbackRuleBucket::getPriority).reversed()
                        .thenComparing(Comparator.comparingInt(FeedbackRuleBucket::getEvidenceCount).reversed())
                        .thenComparing(FeedbackRuleBucket::getCategory))
                .limit(MAX_ACTIVE_FEEDBACK_RULES)
                .map(FeedbackRuleBucket::toResponse)
                .toList();
    }

    private boolean hasManualFeedbackSignal(EvaluationRecordDto record) {
        if (record == null) {
            return false;
        }
        if (record.getHumanScore() != null && record.getHumanScore() <= 2) {
            return true;
        }
        String note = safeText(record.getReviewNote());
        return !note.isBlank() && (containsAny(note, NOTE_SAFETY_TRIGGERS)
                || containsAny(note, NOTE_KNOWLEDGE_TRIGGERS)
                || containsAny(note, NOTE_ACCURACY_TRIGGERS)
                || containsAny(note, NOTE_STRUCTURE_TRIGGERS));
    }

    private boolean isAutoFeedbackCandidate(EvaluationRecordDto record) {
        if (record == null) {
            return false;
        }
        boolean lowAutoScore = record.getAutoScore() != null && record.getAutoScore() < AUTO_REVIEW_THRESHOLD;
        String suggestedAction = safeText(record.getSuggestedAction());
        return lowAutoScore || (!suggestedAction.isBlank() && !"持续观察".equals(suggestedAction));
    }

    private Set<String> resolveFeedbackCategories(EvaluationRecordDto record, boolean manualSignal) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        String suggestedAction = safeText(record.getSuggestedAction());
        String reviewNote = safeText(record.getReviewNote());
        EvaluationRubricScore score = record == null ? null : record.getScoreBreakdown();

        if ("补安全规则".equals(suggestedAction)
                || (score != null && safeInteger(score.getSafetyScore()) <= 10)
                || containsAny(reviewNote, NOTE_SAFETY_TRIGGERS)) {
            categories.add(RULE_SAFETY);
        }
        if ("补知识库".equals(suggestedAction)
                || containsAny(reviewNote, NOTE_KNOWLEDGE_TRIGGERS)) {
            categories.add(RULE_KNOWLEDGE);
        }
        if ("调检索与提示词".equals(suggestedAction)
                || (score != null && safeInteger(score.getAccuracyScore()) <= 13)
                || containsAny(reviewNote, NOTE_ACCURACY_TRIGGERS)) {
            categories.add(RULE_ACCURACY);
        }
        if ("优化回答结构".equals(suggestedAction)
                || (score != null && safeInteger(score.getCompletenessScore()) <= 14)
                || (score != null && safeInteger(score.getActionabilityScore()) <= 14)
                || containsAny(reviewNote, NOTE_STRUCTURE_TRIGGERS)) {
            categories.add(RULE_STRUCTURE);
        }

        if (categories.isEmpty() && (manualSignal || isAutoFeedbackCandidate(record))) {
            categories.add(RULE_ACCURACY);
        }
        return categories;
    }

    private FeedbackRuleBucket newFeedbackRuleBucket(String category) {
        return new FeedbackRuleBucket(
                category,
                "feedback-rule-" + category,
                feedbackRuleTitle(category),
                feedbackRuleInstruction(category)
        );
    }

    private String feedbackRuleTitle(String category) {
        return switch (category) {
            case RULE_SAFETY -> "收紧安全用药边界";
            case RULE_KNOWLEDGE -> "先说明证据不足";
            case RULE_ACCURACY -> "先回应问题核心";
            case RULE_STRUCTURE -> "使用固定回答结构";
            default -> "补充回答约束";
        };
    }

    private String feedbackRuleInstruction(String category) {
        return switch (category) {
            case RULE_SAFETY -> "涉及药剂、倍数、复喷或安全间隔期时，必须只引用参考资料里明确支持的内容；资料不足时先说明“不足以判断”，不要补编剂量或操作。";
            case RULE_KNOWLEDGE -> "当参考资料或图谱证据不足时，先说明当前证据不足，并建议管理员补充知识库；不要为了完整而编造来源、病名或结论。";
            case RULE_ACCURACY -> "回答开头先直接回应农户问题核心，再给判断依据；不要只反问，也不要把问题带到无关主题。";
            case RULE_STRUCTURE -> "优先按“判断依据 + 处理建议 + 风险提醒 + 下一步”组织回答，让农户看完就知道该巡园、复查、补图还是查看方案。";
            default -> "优先依据评测低分原因补强回答质量。";
        };
    }

    private boolean isDiseaseOverviewQuestion(String question) {
        if (question == null) {
            return false;
        }
        return question.contains("病害") && (question.contains("什么") || question.contains("哪些") || question.contains("常见"));
    }

    private boolean looksLikeFollowUpQuestion(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String normalized = answer.trim();
        long questionMarks = normalized.chars().filter(ch -> ch == '？' || ch == '?').count();
        return questionMarks > 0 && !containsAny(normalized, ACTION_KEYWORDS) && normalized.length() < 80;
    }

    private boolean containsUnsupportedAction(String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        if (answer == null || !answer.contains("复喷")) {
            return false;
        }
        return !collectSourceText(sources).replaceAll("\\s+", "").contains("复喷");
    }

    private boolean containsUnsupportedDosage(String answer, List<com.litchi.dto.ChatResponse.Source> sources) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?\\s*倍").matcher(answer);
        if (!matcher.find()) {
            return false;
        }
        String evidence = collectSourceText(sources).replaceAll("\\s+", "");
        matcher.reset();
        while (matcher.find()) {
            String dosage = matcher.group().replaceAll("\\s+", "");
            if (!evidence.contains(dosage)) {
                return true;
            }
        }
        return false;
    }

    private String collectSourceText(List<com.litchi.dto.ChatResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        return sources.stream()
                .map(source -> safeText(source.getContent()))
                .collect(Collectors.joining("\n"));
    }

    private Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum / values.size() * 1000.0) / 1000.0;
    }

    private boolean isWithinDays(String createdAt, OffsetDateTime endExclusive, int days) {
        if (createdAt == null || createdAt.isBlank()) {
            return false;
        }
        try {
            OffsetDateTime created = OffsetDateTime.parse(createdAt);
            OffsetDateTime startInclusive = endExclusive.minusDays(days);
            return !created.isBefore(startInclusive) && created.isBefore(endExclusive.plusSeconds(1));
        } catch (Exception ignored) {
            return false;
        }
    }

    private int countKeywordHits(String text, List<String> keywords) {
        String normalized = normalizeText(text);
        int count = 0;
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeText(keyword);
            if (!normalizedKeyword.isBlank() && normalized.contains(normalizedKeyword)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String text, List<String> keywords) {
        return countKeywordHits(text, keywords) > 0;
    }

    private double keywordCoverage(List<String> keywords, String text) {
        if (keywords == null || keywords.isEmpty()) {
            return 0.0;
        }
        String normalized = normalizeText(text);
        long hit = keywords.stream()
                .map(this::normalizeText)
                .filter(keyword -> !keyword.isBlank() && normalized.contains(keyword))
                .distinct()
                .count();
        return Math.min(1.0, (double) hit / keywords.size());
    }

    private double lexicalSimilarity(String left, String right) {
        Set<String> leftNgrams = characterNgrams(normalizeText(left));
        Set<String> rightNgrams = characterNgrams(normalizeText(right));
        if (leftNgrams.isEmpty() || rightNgrams.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new LinkedHashSet<>(leftNgrams);
        intersection.retainAll(rightNgrams);
        Set<String> union = new LinkedHashSet<>(leftNgrams);
        union.addAll(rightNgrams);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> characterNgrams(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        if (text.length() < 2) {
            return Set.of(text);
        }
        Set<String> ngrams = new LinkedHashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            ngrams.add(text.substring(i, i + 2));
        }
        return ngrams;
    }

    private int clampScore(double score) {
        return (int) Math.max(0, Math.min(25, Math.round(score)));
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveReviewStatus(Double autoScore, ReviewState reviewState) {
        if (reviewState != null && (reviewState.getHumanScore() != null || trimToNull(reviewState.getReviewNote()) != null)) {
            return "reviewed";
        }
        if (autoScore != null && autoScore < AUTO_REVIEW_THRESHOLD) {
            return "pending";
        }
        return "not_needed";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\p{Punct}，。；：！？、“”‘’（）()【】《》\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private void loadState() {
        if (!Files.exists(statePath)) {
            return;
        }
        try {
            StateSnapshot snapshot = objectMapper.readValue(statePath.toFile(), StateSnapshot.class);
            reviewStateMap.clear();
            if (snapshot.getReviews() != null) {
                for (ReviewState review : snapshot.getReviews()) {
                    if (review.getId() != null && !review.getId().isBlank()) {
                        reviewStateMap.put(review.getId(), review);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load evaluation review state, starting fresh", e);
            reviewStateMap.clear();
        }
    }

    private void persistState() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(statePath.toFile(), new StateSnapshot(new ArrayList<>(reviewStateMap.values())));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist evaluation review state", e);
        }
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path applicationDir = new ApplicationHome(EvaluationService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    private static class FeedbackRuleBucket {
        private final String category;
        private final String id;
        private final String title;
        private final String instruction;
        private int evidenceCount;
        private int priority;
        private boolean hasHumanEvidence;
        private boolean hasAutoEvidence;

        private FeedbackRuleBucket(String category, String id, String title, String instruction) {
            this.category = category;
            this.id = id;
            this.title = title;
            this.instruction = instruction;
        }

        private void addEvidence(String sourceType, int weight) {
            evidenceCount++;
            priority += weight;
            if ("human_review".equals(sourceType)) {
                hasHumanEvidence = true;
            } else {
                hasAutoEvidence = true;
            }
        }

        private String getCategory() {
            return category;
        }

        private int getEvidenceCount() {
            return evidenceCount;
        }

        private int getPriority() {
            return priority;
        }

        private EvaluationStatsResponse.ActiveFeedbackRule toResponse() {
            return EvaluationStatsResponse.ActiveFeedbackRule.builder()
                    .id(id)
                    .category(category)
                    .title(title)
                    .instruction(instruction)
                    .sourceType(resolveSourceType())
                    .evidenceCount(evidenceCount)
                    .priority(priority)
                    .build();
        }

        private String resolveSourceType() {
            if (hasHumanEvidence && hasAutoEvidence) {
                return "mixed";
            }
            return hasHumanEvidence ? "human_review" : "auto_score";
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReviewState {
        private String id;
        private Integer humanScore;
        private String reviewNote;
        private String reviewStatus;
        private String reviewedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<ReviewState> reviews = new ArrayList<>();
    }
}
