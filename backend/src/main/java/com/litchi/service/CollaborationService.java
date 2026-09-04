package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.auth.AuthenticatedUser;
import com.litchi.dto.ConsultationRecordDto;
import com.litchi.dto.CreateConsultationRequest;
import com.litchi.dto.RecommendedPlanDto;
import com.litchi.dto.RemedyPlanDto;
import com.litchi.dto.SaveRemedyPlanRequest;
import com.litchi.dto.PageResponse;
import com.litchi.dto.ShopTrendDto;
import com.litchi.dto.StoreProfileDto;
import com.litchi.dto.UpsertStoreProfileRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationService {

    private static final List<String> CONSULTATION_STATUSES = List.of("pending", "contacted", "completed");

    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;

    @Value("${app.collaboration.state-file:data/collaboration-state.json}")
    private String stateFile;

    private final List<StoreProfileRecord> profiles = new ArrayList<>();
    private final List<RemedyPlanRecord> plans = new ArrayList<>();
    private final List<ConsultationRecord> consultations = new ArrayList<>();

    private Path statePath;

    @PostConstruct
    public void init() {
        statePath = resolvePath(stateFile);
        try {
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare collaboration state directory", e);
        }
        loadState();
        if (profiles.isEmpty() && plans.isEmpty()) {
            seedDefaults();
            persistState();
        }
    }

    public synchronized StoreProfileDto getProfile(AuthenticatedUser user) {
        return toProfileDto(ensureShopProfile(user));
    }

    public synchronized StoreProfileDto upsertProfile(AuthenticatedUser user, UpsertStoreProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺资料不能为空。");
        }

        StoreProfileRecord profile = ensureShopProfile(user);
        profile.setShopName(requireText(request.getShopName(), "店铺名称"));
        profile.setContactName(requireText(request.getContactName(), "联系人"));
        profile.setPhone(trim(request.getPhone()));
        profile.setWechat(trim(request.getWechat()));
        profile.setAddress(trim(request.getAddress()));
        profile.setServiceArea(trim(request.getServiceArea()));
        profile.setSpecialties(trim(request.getSpecialties()));
        profile.setRating(normalizeRating(request.getRating()));
        profile.setUpdatedAt(now());
        syncOwnedPlanShopName(profile);
        persistState();
        log.info("Shop profile upserted for user={}", user.username());
        return toProfileDto(profile);
    }

    public synchronized PageResponse<RemedyPlanDto> listPlans(AuthenticatedUser user, int page, int size) {
        ensureShopProfile(user);
        List<RemedyPlanDto> all = plans.stream()
                .filter(plan -> isOwnedBy(user, plan.getOwnerId(), plan.getOwnerUsername()))
                .sorted(Comparator.comparing(RemedyPlanRecord::getUpdatedAt).reversed())
                .map(this::toPlanDto)
                .toList();
        return paginate(all, page, size);
    }

    public synchronized RemedyPlanDto createPlan(AuthenticatedUser user, SaveRemedyPlanRequest request) {
        String idempotencyKey = request == null ? null : trim(request.getIdempotencyKey());
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            RemedyPlanRecord existing = plans.stream()
                    .filter(plan -> idempotencyKey.equals(plan.getIdempotencyKey()))
                    .filter(plan -> isOwnedBy(user, plan.getOwnerId(), plan.getOwnerUsername()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                log.info("Remedy plan idempotency hit key={} planId={} for user={}",
                        idempotencyKey, existing.getId(), user.username());
                return toPlanDto(existing);
            }
        }
        StoreProfileRecord profile = ensureShopProfile(user);
        RemedyPlanRecord record = buildPlanRecord(null, profile, user, request);
        plans.add(record);
        persistState();
        log.info("Remedy plan created id={} for user={}", record.getId(), user.username());
        return toPlanDto(record);
    }

    public synchronized RemedyPlanDto updatePlan(AuthenticatedUser user, String id, SaveRemedyPlanRequest request) {
        RemedyPlanRecord record = findOwnedPlan(user, id);
        StoreProfileRecord profile = ensureShopProfile(user);
        applyPlanRequest(record, profile, user, request);
        persistState();
        log.info("Remedy plan updated id={} for user={}", id, user.username());
        return toPlanDto(record);
    }

    public synchronized Map<String, Object> deletePlan(AuthenticatedUser user, String id) {
        RemedyPlanRecord record = findOwnedPlan(user, id);
        plans.remove(record);
        persistState();
        log.info("Remedy plan deleted id={} for user={}", id, user.username());
        return Map.of("deleted", true, "message", "方案已删除。");
    }

    public synchronized List<ShopTrendDto> getTrends() {
        Map<String, List<ConsultationRecord>> byDisease = new LinkedHashMap<>();
        for (ConsultationRecord record : consultations) {
            byDisease.computeIfAbsent(record.getDiseaseTag(), key -> new ArrayList<>()).add(record);
        }

        OffsetDateTime threshold = OffsetDateTime.now().minusDays(7);
        return byDisease.entrySet().stream()
                .map(entry -> {
                    List<ConsultationRecord> items = entry.getValue();
                    String latestAt = items.stream()
                            .map(ConsultationRecord::getUpdatedAt)
                            .filter(value -> value != null && !value.isBlank())
                            .max(String::compareTo)
                            .orElse("");
                    long recent = items.stream()
                            .filter(item -> parseDate(item.getCreatedAt()).map(date -> date.isAfter(threshold)).orElse(false))
                            .count();
                    return ShopTrendDto.builder()
                            .diseaseTag(entry.getKey())
                            .totalConsultations(items.size())
                            .recentConsultations(recent)
                            .latestAt(latestAt)
                            .build();
                })
                .sorted(Comparator.comparingLong(ShopTrendDto::getTotalConsultations).reversed()
                        .thenComparing(ShopTrendDto::getLatestAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public synchronized List<RecommendedPlanDto> getRecommendations(String diseaseTag, String stageTag, String query, AuthenticatedUser user) {
        String normalizedDisease = trim(diseaseTag);
        String normalizedStage = trim(stageTag);
        String normalizedQuery = trim(query);
        if (normalizedDisease.isBlank() && normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("请先提供病症标签或问题描述。");
        }

        List<RemedyPlanRecord> activePlans = plans.stream().filter(RemedyPlanRecord::isActive).toList();
        if (activePlans.isEmpty()) {
            return List.of();
        }

        List<RecommendedPlanDto> ranked = activePlans.stream()
                .map(plan -> rankPlan(plan, normalizedDisease, normalizedStage, normalizedQuery, user))
                .filter(item -> item.getScore() >= 18 || normalizedDisease.isBlank())
                .sorted(Comparator.comparing(RecommendedPlanDto::getScore).reversed()
                        .thenComparing(RecommendedPlanDto::getRating, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .toList();

        if (!ranked.isEmpty()) {
            return ranked;
        }

        return activePlans.stream()
                .map(plan -> rankPlan(plan, "", normalizedStage, normalizedQuery, user))
                .sorted(Comparator.comparing(RecommendedPlanDto::getScore).reversed())
                .limit(6)
                .toList();
    }

    public synchronized ConsultationRecordDto createConsultation(AuthenticatedUser user, CreateConsultationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("求助内容不能为空。");
        }

        RemedyPlanRecord plan = findPlan(request.getPlanId());
        if (!plan.isActive()) {
            throw new IllegalArgumentException("当前方案未启用，无法提交求助。");
        }

        StoreProfileRecord profile = findProfile(plan.getShopId())
                .orElseThrow(() -> new IllegalArgumentException("门店资料不存在。"));
        String createdAt = now();
        ConsultationRecord record = ConsultationRecord.builder()
                .id(uuid())
                .farmerUserId(user.id())
                .farmerUsername(user.username())
                .diseaseTag(fallbackText(request.getDiseaseTag(), plan.getDiseaseTag()))
                .stageTag(fallbackText(request.getStageTag(), plan.getStageTag()))
                .question(trim(request.getQuestion()))
                .planId(plan.getId())
                .planTitle(plan.getTitle())
                .shopId(profile.getShopId())
                .shopName(profile.getShopName())
                .status("pending")
                .reasonTags(normalizeList(request.getReasonTags(), 6))
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
        consultations.add(record);
        persistState();
        log.info("Consultation created id={} for user={}", record.getId(), user.username());
        return toConsultationDto(record);
    }

    public synchronized PageResponse<ConsultationRecordDto> listMyConsultations(AuthenticatedUser user, int page, int size) {
        List<ConsultationRecordDto> all = consultations.stream()
                .filter(record -> user.id().equals(record.getFarmerUserId()))
                .sorted(Comparator.comparing(ConsultationRecord::getUpdatedAt).reversed())
                .map(this::toConsultationDto)
                .toList();
        return paginate(all, page, size);
    }

    public synchronized PageResponse<ConsultationRecordDto> listInbox(AuthenticatedUser user, int page, int size) {
        List<String> ownShopIds = profiles.stream()
                .filter(profile -> isOwnedBy(user, profile.getOwnerId(), profile.getOwnerUsername()))
                .map(StoreProfileRecord::getShopId)
                .toList();

        List<ConsultationRecordDto> all = consultations.stream()
                .filter(record -> ownShopIds.contains(record.getShopId()))
                .sorted(Comparator.comparing(ConsultationRecord::getUpdatedAt).reversed())
                .map(this::toConsultationDto)
                .toList();
        return paginate(all, page, size);
    }

    public synchronized ConsultationRecordDto updateConsultationStatus(AuthenticatedUser user, String id, String status) {
        ConsultationRecord record = consultations.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("求助记录不存在。"));

        boolean allowed = profiles.stream()
                .filter(profile -> record.getShopId().equals(profile.getShopId()))
                .anyMatch(profile -> isOwnedBy(user, profile.getOwnerId(), profile.getOwnerUsername()));
        if (!allowed) {
            throw new IllegalArgumentException("当前账号不能处理这条求助。");
        }

        record.setStatus(normalizeStatus(status));
        record.setUpdatedAt(now());
        persistState();
        log.info("Consultation status updated id={} status={} for user={}", id, status, user.username());
        return toConsultationDto(record);
    }

    public synchronized CollaborationSummary getSummary() {
        long activePlans = plans.stream().filter(RemedyPlanRecord::isActive).count();
        long pendingConsultations = consultations.stream().filter(record -> "pending".equals(record.getStatus())).count();
        String topDisease = getTrends().stream().findFirst().map(ShopTrendDto::getDiseaseTag).orElse("暂无");
        Double avgShopRating = profiles.isEmpty()
                ? null
                : Math.round(profiles.stream().mapToDouble(StoreProfileRecord::getRating).average().orElse(0) * 100.0) / 100.0;

        return new CollaborationSummary(activePlans, consultations.size(), pendingConsultations, topDisease, avgShopRating);
    }

    private <T> PageResponse<T> paginate(List<T> list, int page, int size) {
        int total = list.size();
        int start = (page - 1) * size;
        if (start < 0) {
            start = 0;
        }
        if (start >= total) {
            return PageResponse.<T>builder().total(total).page(page).size(size).items(List.of()).build();
        }
        int end = Math.min(start + size, total);
        return PageResponse.<T>builder().total(total).page(page).size(size).items(list.subList(start, end)).build();
    }

    private RecommendedPlanDto rankPlan(RemedyPlanRecord plan, String diseaseTag, String stageTag, String query, AuthenticatedUser user) {
        StoreProfileRecord profile = findProfile(plan.getShopId()).orElseGet(() -> placeholderProfile(plan));
        double diseaseScore = scoreDiseaseMatch(plan, diseaseTag, query);
        double qualityScore = scorePlanQuality(plan);
        double responseScore = scoreShopResponse(plan.getShopId(), profile.getRating());
        double collaborativeScore = scoreCollaborativeSignal(plan, diseaseTag, user);
        double totalScore = Math.round((diseaseScore + qualityScore + responseScore + collaborativeScore) * 100.0) / 100.0;
        List<String> reasonTags = buildReasonTags(plan, profile, diseaseScore, responseScore, collaborativeScore, stageTag);

        return RecommendedPlanDto.builder()
                .planId(plan.getId())
                .shopId(profile.getShopId())
                .shopName(profile.getShopName())
                .contactName(profile.getContactName())
                .phone(profile.getPhone())
                .wechat(profile.getWechat())
                .address(profile.getAddress())
                .serviceArea(profile.getServiceArea())
                .rating(profile.getRating())
                .title(plan.getTitle())
                .diseaseTag(plan.getDiseaseTag())
                .stageTag(plan.getStageTag())
                .summary(plan.getSummary())
                .products(copyList(plan.getProducts()))
                .usageTips(copyList(plan.getUsageTips()))
                .riskNotes(copyList(plan.getRiskNotes()))
                .inventoryStatus(plan.getInventoryStatus())
                .score(totalScore)
                .reasonTags(reasonTags)
                .build();
    }

    private StoreProfileRecord ensureShopProfile(AuthenticatedUser user) {
        return profiles.stream()
                .filter(profile -> isOwnedBy(user, profile.getOwnerId(), profile.getOwnerUsername()))
                .findFirst()
                .map(profile -> {
                    if ((profile.getOwnerId() == null || profile.getOwnerId().isBlank()) && user.id() != null) {
                        profile.setOwnerId(user.id());
                        profile.setUpdatedAt(now());
                    }
                    return profile;
                })
                .orElseGet(() -> {
                    String timestamp = now();
                    StoreProfileRecord created = StoreProfileRecord.builder()
                            .shopId(uuid())
                            .ownerId(user.id())
                            .ownerUsername(user.username())
                            .shopName(user.username() + "门店")
                            .contactName(user.username())
                            .phone("")
                            .wechat("")
                            .address("")
                            .serviceArea("广西荔枝产区")
                            .specialties("病害咨询、用药建议")
                            .rating(4.6)
                            .createdAt(timestamp)
                            .updatedAt(timestamp)
                            .build();
                    profiles.add(created);
                    persistState();
                    return created;
                });
    }

    private RemedyPlanRecord buildPlanRecord(String id, StoreProfileRecord profile, AuthenticatedUser user, SaveRemedyPlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("方案内容不能为空。");
        }

        String timestamp = now();
        RemedyPlanRecord record = RemedyPlanRecord.builder()
                .id(id == null ? uuid() : id)
                .shopId(profile.getShopId())
                .ownerId(user.id())
                .ownerUsername(user.username())
                .shopName(profile.getShopName())
                .createdAt(id == null ? timestamp : null)
                .updatedAt(timestamp)
                .build();
        applyPlanRequest(record, profile, user, request);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(timestamp);
        }
        return record;
    }

    private void applyPlanRequest(RemedyPlanRecord record, StoreProfileRecord profile, AuthenticatedUser user, SaveRemedyPlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("方案内容不能为空。");
        }

        record.setShopId(profile.getShopId());
        record.setOwnerId(user.id());
        record.setOwnerUsername(user.username());
        record.setShopName(profile.getShopName());
        record.setTitle(requireText(request.getTitle(), "方案标题"));
        record.setDiseaseTag(requireText(request.getDiseaseTag(), "病症标签"));
        record.setStageTag(requireText(request.getStageTag(), "适用阶段"));
        record.setSummary(requireText(request.getSummary(), "方案摘要"));
        record.setProducts(normalizeList(request.getProducts(), 10));
        record.setUsageTips(normalizeList(request.getUsageTips(), 8));
        record.setRiskNotes(normalizeList(request.getRiskNotes(), 8));
        record.setInventoryStatus(fallbackText(request.getInventoryStatus(), "有现货"));
        record.setActive(request.getActive() == null || request.getActive());
        record.setIdempotencyKey(trim(request.getIdempotencyKey()));
        record.setUpdatedAt(now());
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(record.getUpdatedAt());
        }
    }

    private RemedyPlanRecord findOwnedPlan(AuthenticatedUser user, String id) {
        return plans.stream()
                .filter(plan -> plan.getId().equals(id))
                .filter(plan -> isOwnedBy(user, plan.getOwnerId(), plan.getOwnerUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("方案不存在或当前账号无权操作。"));
    }

    private RemedyPlanRecord findPlan(String id) {
        return plans.stream()
                .filter(plan -> plan.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("方案不存在。"));
    }

    private Optional<StoreProfileRecord> findProfile(String shopId) {
        return profiles.stream().filter(profile -> profile.getShopId().equals(shopId)).findFirst();
    }

    private void syncOwnedPlanShopName(StoreProfileRecord profile) {
        plans.stream()
                .filter(plan -> profile.getShopId().equals(plan.getShopId()))
                .forEach(plan -> {
                    plan.setShopName(profile.getShopName());
                    plan.setUpdatedAt(now());
                });
    }

    private double scoreDiseaseMatch(RemedyPlanRecord plan, String diseaseTag, String query) {
        String planDisease = normalize(plan.getDiseaseTag());
        String text = String.join(" ",
                normalize(plan.getTitle()),
                normalize(plan.getSummary()),
                String.join(" ", copyList(plan.getProducts())));

        if (!diseaseTag.isBlank()) {
            String needle = normalize(diseaseTag);
            if (planDisease.equals(needle)) {
                return 42;
            }
            if (planDisease.contains(needle) || needle.contains(planDisease)) {
                return 34;
            }
            if (text.contains(needle)) {
                return 28;
            }
            return 8;
        }

        if (query.isBlank()) {
            return 20;
        }

        String needle = normalize(query);
        return text.contains(needle) || planDisease.contains(needle) ? 26 : 14;
    }

    private double scorePlanQuality(RemedyPlanRecord plan) {
        double score = 8;
        score += Math.min(plan.getProducts().size(), 4) * 3;
        score += Math.min(plan.getUsageTips().size(), 4) * 2;
        score += Math.min(plan.getRiskNotes().size(), 4) * 2;
        score += plan.getSummary().length() >= 18 ? 3 : 0;
        score += plan.isActive() ? 2 : 0;
        return Math.min(score, 25);
    }

    private double scoreShopResponse(String shopId, Double rating) {
        long total = consultations.stream().filter(record -> shopId.equals(record.getShopId())).count();
        long handled = consultations.stream()
                .filter(record -> shopId.equals(record.getShopId()))
                .filter(record -> !"pending".equals(record.getStatus()))
                .count();
        double responseRatio = total == 0 ? 0.65 : (double) handled / total;
        double ratingScore = rating == null ? 8 : Math.min(12, rating / 5.0 * 12);
        return Math.round((responseRatio * 10 + ratingScore) * 100.0) / 100.0;
    }

    private double scoreCollaborativeSignal(RemedyPlanRecord plan, String diseaseTag, AuthenticatedUser user) {
        if (consultations.isEmpty()) {
            return 4;
        }

        String normalizedDisease = normalize(diseaseTag);
        long sameDiseaseSelections = consultations.stream()
                .filter(record -> normalizedDisease.isBlank() || normalize(record.getDiseaseTag()).equals(normalizedDisease))
                .filter(record -> plan.getId().equals(record.getPlanId()))
                .count();
        long sameShopSelections = consultations.stream().filter(record -> plan.getShopId().equals(record.getShopId())).count();
        long ownHistory = consultations.stream()
                .filter(record -> user != null && user.id().equals(record.getFarmerUserId()))
                .filter(record -> plan.getShopId().equals(record.getShopId()))
                .count();
        return Math.min(18, sameDiseaseSelections * 3 + Math.min(sameShopSelections, 4) + ownHistory * 2 + 3);
    }

    private List<String> buildReasonTags(
            RemedyPlanRecord plan,
            StoreProfileRecord profile,
            double diseaseScore,
            double responseScore,
            double collaborativeScore,
            String stageTag
    ) {
        List<String> reasons = new ArrayList<>();
        if (diseaseScore >= 34) {
            reasons.add("病症高度匹配");
        }
        if (!stageTag.isBlank() && normalize(plan.getStageTag()).contains(normalize(stageTag))) {
            reasons.add("适配当前阶段");
        }
        if (responseScore >= 18) {
            reasons.add("门店响应稳定");
        }
        if (collaborativeScore >= 10) {
            reasons.add("相似场景常被选择");
        }
        if (profile.getRating() != null && profile.getRating() >= 4.7) {
            reasons.add("门店评分较高");
        }
        if (!"缺货".equals(plan.getInventoryStatus())) {
            reasons.add("当前可承接");
        }
        if (reasons.isEmpty()) {
            reasons.add("综合规则推荐");
        }
        return reasons;
    }

    private StoreProfileRecord placeholderProfile(RemedyPlanRecord plan) {
        return StoreProfileRecord.builder()
                .shopId(plan.getShopId())
                .ownerId(plan.getOwnerId())
                .ownerUsername(plan.getOwnerUsername())
                .shopName(plan.getShopName())
                .contactName(plan.getOwnerUsername())
                .phone("")
                .wechat("")
                .address("")
                .serviceArea("")
                .specialties("")
                .rating(4.5)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private StoreProfileDto toProfileDto(StoreProfileRecord record) {
        return StoreProfileDto.builder()
                .shopId(record.getShopId())
                .ownerId(record.getOwnerId())
                .ownerUsername(record.getOwnerUsername())
                .shopName(record.getShopName())
                .contactName(record.getContactName())
                .phone(record.getPhone())
                .wechat(record.getWechat())
                .address(record.getAddress())
                .serviceArea(record.getServiceArea())
                .specialties(record.getSpecialties())
                .rating(record.getRating())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private RemedyPlanDto toPlanDto(RemedyPlanRecord record) {
        return RemedyPlanDto.builder()
                .id(record.getId())
                .shopId(record.getShopId())
                .ownerId(record.getOwnerId())
                .ownerUsername(record.getOwnerUsername())
                .shopName(record.getShopName())
                .title(record.getTitle())
                .diseaseTag(record.getDiseaseTag())
                .stageTag(record.getStageTag())
                .summary(record.getSummary())
                .products(copyList(record.getProducts()))
                .usageTips(copyList(record.getUsageTips()))
                .riskNotes(copyList(record.getRiskNotes()))
                .inventoryStatus(record.getInventoryStatus())
                .active(record.isActive())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .idempotencyKey(record.getIdempotencyKey())
                .build();
    }

    private ConsultationRecordDto toConsultationDto(ConsultationRecord record) {
        StoreProfileRecord profile = findProfile(record.getShopId()).orElseGet(() -> StoreProfileRecord.builder()
                .shopId(record.getShopId())
                .shopName(record.getShopName())
                .contactName("")
                .phone("")
                .wechat("")
                .build());

        return ConsultationRecordDto.builder()
                .id(record.getId())
                .farmerUserId(record.getFarmerUserId())
                .farmerUsername(record.getFarmerUsername())
                .diseaseTag(record.getDiseaseTag())
                .stageTag(record.getStageTag())
                .question(record.getQuestion())
                .planId(record.getPlanId())
                .planTitle(record.getPlanTitle())
                .shopId(record.getShopId())
                .shopName(record.getShopName())
                .contactName(profile.getContactName())
                .phone(profile.getPhone())
                .wechat(profile.getWechat())
                .status(record.getStatus())
                .reasonTags(copyList(record.getReasonTags()))
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private void loadState() {
        if (statePath != null && Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), StateSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load collaboration state from {}", statePath, e);
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            sanitizeLoadedState();
            return;
        }

        Optional<MysqlStateStoreService.CollaborationStateData> mysqlState = mysqlStateStoreService.loadCollaborationState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            sanitizeLoadedState();
            return;
        }

        if (!profiles.isEmpty() || !plans.isEmpty() || !consultations.isEmpty()) {
            mysqlStateStoreService.saveCollaborationState(
                    toMysqlState(new StateSnapshot(new ArrayList<>(profiles), new ArrayList<>(plans), new ArrayList<>(consultations)))
            );
        }
        sanitizeLoadedState();
    }

    private void applyState(StateSnapshot snapshot) {
        profiles.clear();
        plans.clear();
        consultations.clear();
        if (snapshot != null) {
            profiles.addAll(copyList(snapshot.getProfiles()));
            plans.addAll(copyList(snapshot.getPlans()));
            consultations.addAll(copyList(snapshot.getConsultations()));
        }
    }

    private StateSnapshot fromMysqlState(MysqlStateStoreService.CollaborationStateData state) {
        List<StoreProfileRecord> mysqlProfiles = state.getProfiles() == null
                ? List.of()
                : state.getProfiles().stream()
                .map(profile -> StoreProfileRecord.builder()
                        .shopId(profile.getShopId())
                        .ownerId(profile.getOwnerId())
                        .ownerUsername(profile.getOwnerUsername())
                        .shopName(profile.getShopName())
                        .contactName(profile.getContactName())
                        .phone(profile.getPhone())
                        .wechat(profile.getWechat())
                        .address(profile.getAddress())
                        .serviceArea(profile.getServiceArea())
                        .specialties(profile.getSpecialties())
                        .rating(profile.getRating())
                        .createdAt(profile.getCreatedAt())
                        .updatedAt(profile.getUpdatedAt())
                        .build())
                .toList();

        List<RemedyPlanRecord> mysqlPlans = state.getPlans() == null
                ? List.of()
                : state.getPlans().stream()
                .map(plan -> RemedyPlanRecord.builder()
                        .id(plan.getId())
                        .shopId(plan.getShopId())
                        .ownerId(plan.getOwnerId())
                        .ownerUsername(plan.getOwnerUsername())
                        .shopName(plan.getShopName())
                        .title(plan.getTitle())
                        .diseaseTag(plan.getDiseaseTag())
                        .stageTag(plan.getStageTag())
                        .summary(plan.getSummary())
                        .products(copyList(plan.getProducts()))
                        .usageTips(copyList(plan.getUsageTips()))
                        .riskNotes(copyList(plan.getRiskNotes()))
                        .inventoryStatus(plan.getInventoryStatus())
                        .active(plan.isActive())
                        .createdAt(plan.getCreatedAt())
                        .updatedAt(plan.getUpdatedAt())
                        .idempotencyKey(plan.getIdempotencyKey())
                        .build())
                .toList();

        List<ConsultationRecord> mysqlConsultations = state.getConsultations() == null
                ? List.of()
                : state.getConsultations().stream()
                .map(record -> ConsultationRecord.builder()
                        .id(record.getId())
                        .farmerUserId(record.getFarmerUserId())
                        .farmerUsername(record.getFarmerUsername())
                        .diseaseTag(record.getDiseaseTag())
                        .stageTag(record.getStageTag())
                        .question(record.getQuestion())
                        .planId(record.getPlanId())
                        .planTitle(record.getPlanTitle())
                        .shopId(record.getShopId())
                        .shopName(record.getShopName())
                        .status(record.getStatus())
                        .reasonTags(copyList(record.getReasonTags()))
                        .createdAt(record.getCreatedAt())
                        .updatedAt(record.getUpdatedAt())
                        .build())
                .toList();

        return new StateSnapshot(mysqlProfiles, mysqlPlans, mysqlConsultations);
    }

    private MysqlStateStoreService.CollaborationStateData toMysqlState(StateSnapshot snapshot) {
        List<MysqlStateStoreService.StoreProfileData> mysqlProfiles = snapshot.getProfiles() == null
                ? List.of()
                : snapshot.getProfiles().stream()
                .map(profile -> new MysqlStateStoreService.StoreProfileData(
                        profile.getShopId(),
                        profile.getOwnerId(),
                        profile.getOwnerUsername(),
                        profile.getShopName(),
                        profile.getContactName(),
                        profile.getPhone(),
                        profile.getWechat(),
                        profile.getAddress(),
                        profile.getServiceArea(),
                        profile.getSpecialties(),
                        profile.getRating(),
                        profile.getCreatedAt(),
                        profile.getUpdatedAt()
                ))
                .toList();

        List<MysqlStateStoreService.RemedyPlanData> mysqlPlans = snapshot.getPlans() == null
                ? List.of()
                : snapshot.getPlans().stream()
                .map(plan -> new MysqlStateStoreService.RemedyPlanData(
                        plan.getId(),
                        plan.getShopId(),
                        plan.getOwnerId(),
                        plan.getOwnerUsername(),
                        plan.getShopName(),
                        plan.getTitle(),
                        plan.getDiseaseTag(),
                        plan.getStageTag(),
                        plan.getSummary(),
                        copyList(plan.getProducts()),
                        copyList(plan.getUsageTips()),
                        copyList(plan.getRiskNotes()),
                        plan.getInventoryStatus(),
                        plan.isActive(),
                        plan.getCreatedAt(),
                        plan.getUpdatedAt(),
                        plan.getIdempotencyKey()
                ))
                .toList();

        List<MysqlStateStoreService.ConsultationData> mysqlConsultations = snapshot.getConsultations() == null
                ? List.of()
                : snapshot.getConsultations().stream()
                .map(record -> new MysqlStateStoreService.ConsultationData(
                        record.getId(),
                        record.getFarmerUserId(),
                        record.getFarmerUsername(),
                        record.getDiseaseTag(),
                        record.getStageTag(),
                        record.getQuestion(),
                        record.getPlanId(),
                        record.getPlanTitle(),
                        record.getShopId(),
                        record.getShopName(),
                        record.getStatus(),
                        copyList(record.getReasonTags()),
                        record.getCreatedAt(),
                        record.getUpdatedAt()
                ))
                .toList();

        return new MysqlStateStoreService.CollaborationStateData(mysqlProfiles, mysqlPlans, mysqlConsultations);
    }

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(profiles), new ArrayList<>(plans), new ArrayList<>(consultations));
        persistLocalState(snapshot);
        mysqlStateStoreService.saveCollaborationState(toMysqlState(snapshot));
    }

    private void persistLocalState() {
        persistLocalState(new StateSnapshot(new ArrayList<>(profiles), new ArrayList<>(plans), new ArrayList<>(consultations)));
    }

    private void persistLocalState(StateSnapshot snapshot) {
        if (statePath == null) {
            return;
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist collaboration state", e);
        }
    }

    private void sanitizeLoadedState() {
        boolean changed = false;
        for (ConsultationRecord record : consultations) {
            RemedyPlanRecord plan = plans.stream()
                    .filter(item -> item.getId().equals(record.getPlanId()))
                    .findFirst()
                    .orElse(null);

            if (plan != null && isPlaceholderText(record.getDiseaseTag())) {
                record.setDiseaseTag(plan.getDiseaseTag());
                changed = true;
            }
            if (plan != null && isPlaceholderText(record.getStageTag())) {
                record.setStageTag(plan.getStageTag());
                changed = true;
            }
            if (plan != null && isPlaceholderText(record.getQuestion())) {
                record.setQuestion("根据方案「" + plan.getTitle() + "」发起的协同求助。");
                changed = true;
            }
            if (isPlaceholderText(record.getShopName()) && plan != null) {
                record.setShopName(plan.getShopName());
                changed = true;
            }

            List<String> normalizedReasonTags = normalizeList(record.getReasonTags(), 6).stream()
                    .filter(tag -> !isPlaceholderText(tag))
                    .toList();
            if (!normalizedReasonTags.equals(copyList(record.getReasonTags()))) {
                record.setReasonTags(normalizedReasonTags);
                changed = true;
            }
        }

        if (changed) {
            persistState();
        }
    }

    private void seedDefaults() {
        String seedTime = now();
        StoreProfileRecord seedShopkeeper = StoreProfileRecord.builder()
                .shopId("shop-demo-main")
                .ownerId(null)
                .ownerUsername("shopkeeper")
                .shopName("荔园农资服务站")
                .contactName("门店店主")
                .phone("13800001234")
                .wechat("litchi-shopkeeper")
                .address("广西某示范果园服务点")
                .serviceArea("桂味、妃子笑主产区")
                .specialties("病害咨询、雨季管理、门店回访")
                .rating(4.8)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build();
        StoreProfileRecord seedNorth = StoreProfileRecord.builder()
                .shopId("shop-demo-north")
                .ownerId(null)
                .ownerUsername("demo-north")
                .shopName("丰果农资门店")
                .contactName("刘店长")
                .phone("13900004567")
                .wechat("fengguo-nz")
                .address("示范合作门店 A")
                .serviceArea("北部荔枝园区")
                .specialties("虫害监测、综合管理")
                .rating(4.6)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build();
        StoreProfileRecord seedSouth = StoreProfileRecord.builder()
                .shopId("shop-demo-south")
                .ownerId(null)
                .ownerUsername("demo-south")
                .shopName("雨季病害快配中心")
                .contactName("陈顾问")
                .phone("13700007890")
                .wechat("rain-guard")
                .address("示范合作门店 B")
                .serviceArea("沿海高湿果园")
                .specialties("病害快配、风险提示")
                .rating(4.7)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build();
        profiles.addAll(List.of(seedShopkeeper, seedNorth, seedSouth));

        plans.add(RemedyPlanRecord.builder()
                .id("plan-demo-anthracnose")
                .shopId(seedShopkeeper.getShopId())
                .ownerId(null)
                .ownerUsername("shopkeeper")
                .shopName(seedShopkeeper.getShopName())
                .title("炭疽病雨季处理方案")
                .diseaseTag("炭疽病")
                .stageTag("雨季高湿")
                .summary("适合果面褐斑、连续阴雨后加重的炭疽病场景，强调清园、轮换用药和复查。")
                .products(List.of("吡唑醚菌酯", "苯醚甲环唑", "清园修剪工具包"))
                .usageTips(List.of("优先清理病果病枝", "按标签轮换用药", "3 天内复查果面扩散情况"))
                .riskNotes(List.of("不可脱离标签自行加量", "采收前注意安全间隔期"))
                .inventoryStatus("有现货")
                .active(true)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build());
        plans.add(RemedyPlanRecord.builder()
                .id("plan-demo-blight")
                .shopId(seedSouth.getShopId())
                .ownerId(null)
                .ownerUsername("demo-south")
                .shopName(seedSouth.getShopName())
                .title("霜疫霉病高湿保护方案")
                .diseaseTag("霜疫霉病")
                .stageTag("花果期")
                .summary("适合雨季果面带白霉层、扩散较快的场景，先排水通风，再做保护性处理。")
                .products(List.of("烯酰吗啉", "排水整理服务", "病果清理建议单"))
                .usageTips(List.of("先确认白霉层和排水情况", "优先做环境管理", "必要时联系管理员二次判断"))
                .riskNotes(List.of("病症复杂时不要只靠门店经验判断"))
                .inventoryStatus("有现货")
                .active(true)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build());
        plans.add(RemedyPlanRecord.builder()
                .id("plan-demo-pest")
                .shopId(seedNorth.getShopId())
                .ownerId(null)
                .ownerUsername("demo-north")
                .shopName(seedNorth.getShopName())
                .title("蒂蛀虫监测联动方案")
                .diseaseTag("蒂蛀虫")
                .stageTag("幼果期")
                .summary("适合花果期到幼果期的虫害监测与处理场景，强调先监测、再处置。")
                .products(List.of("诱捕卡", "虫情巡查包", "对应处理建议卡"))
                .usageTips(List.of("先确认果园物候期", "建议拍照或带样复核", "监测结果异常时同步联系管理员"))
                .riskNotes(List.of("不能只卖药不讲监测"))
                .inventoryStatus("预订可配")
                .active(true)
                .createdAt(seedTime)
                .updatedAt(seedTime)
                .build());
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path applicationDir = new ApplicationHome(CollaborationService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    private String requireText(String value, String label) {
        String normalized = trim(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空。");
        }
        return normalized;
    }

    private String fallbackText(String value, String fallback) {
        String normalized = trim(value);
        return normalized.isBlank() ? trim(fallback) : normalized;
    }

    private Double normalizeRating(Double rating) {
        if (rating == null) {
            return 4.6;
        }
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("门店评分必须在 0 到 5 之间。");
        }
        return Math.round(rating * 100.0) / 100.0;
    }

    private String normalizeStatus(String status) {
        String normalized = trim(status).toLowerCase(Locale.ROOT);
        if (!CONSULTATION_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("求助状态必须是 pending、contacted 或 completed。");
        }
        return normalized;
    }

    private boolean isOwnedBy(AuthenticatedUser user, String ownerId, String ownerUsername) {
        if (user == null) {
            return false;
        }
        if (ownerId != null && !ownerId.isBlank() && ownerId.equals(user.id())) {
            return true;
        }
        return ownerUsername != null && !ownerUsername.isBlank() && ownerUsername.equalsIgnoreCase(user.username());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private boolean isPlaceholderText(String value) {
        String normalized = trim(value);
        return normalized.isBlank() || normalized.matches("[?？]+");
    }

    private <T> List<T> copyList(List<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private List<String> normalizeList(List<String> values, int maxItems) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return values.stream()
                .map(this::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(Math.max(maxItems, 1))
                .toList();
    }

    private Optional<OffsetDateTime> parseDate(String value) {
        try {
            return Optional.ofNullable(value).filter(item -> !item.isBlank()).map(OffsetDateTime::parse);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record CollaborationSummary(
            long activePlans,
            long consultationCount,
            long pendingConsultations,
            String topDisease,
            Double avgShopRating
    ) {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<StoreProfileRecord> profiles = new ArrayList<>();
        private List<RemedyPlanRecord> plans = new ArrayList<>();
        private List<ConsultationRecord> consultations = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StoreProfileRecord {
        private String shopId;
        private String ownerId;
        private String ownerUsername;
        private String shopName;
        private String contactName;
        private String phone;
        private String wechat;
        private String address;
        private String serviceArea;
        private String specialties;
        private Double rating;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class RemedyPlanRecord {
        private String id;
        private String shopId;
        private String ownerId;
        private String ownerUsername;
        private String shopName;
        private String title;
        private String diseaseTag;
        private String stageTag;
        private String summary;
        @Builder.Default
        private List<String> products = new ArrayList<>();
        @Builder.Default
        private List<String> usageTips = new ArrayList<>();
        @Builder.Default
        private List<String> riskNotes = new ArrayList<>();
        private String inventoryStatus;
        private boolean active;
        private String createdAt;
        private String updatedAt;
        private String idempotencyKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ConsultationRecord {
        private String id;
        private String farmerUserId;
        private String farmerUsername;
        private String diseaseTag;
        private String stageTag;
        private String question;
        private String planId;
        private String planTitle;
        private String shopId;
        private String shopName;
        private String status;
        @Builder.Default
        private List<String> reasonTags = new ArrayList<>();
        private String createdAt;
        private String updatedAt;
    }
}
