package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatHistoryItem;
import com.litchi.dto.ChatResponse;
import com.litchi.dto.ChatSessionItem;
import com.litchi.dto.PageResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {
    /** 批量落盘周期：请求线程不阻塞在磁盘/MySQL 写，由后台线程定期合并落盘。 */
    private static final long FLUSH_INTERVAL_MS = 2000L;

    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;

    @Value("${app.chat-history.state-file:data/chat-history.json}")
    private String stateFile;

    private final List<ChatRecord> records = new ArrayList<>();
    /** 等待落盘的增量记录，由 flushScheduler 定期消费。 */
    private final Object flushLock = new Object();
    private final List<ChatRecord> pending = new ArrayList<>();
    private ScheduledExecutorService flushScheduler;
    private Path statePath;
    /** 记录上一次 flush 时 MySQL 是否可用，用于检测"不可用→恢复"的转换并对账。 */
    private boolean wasMysqlActive;

    @PostConstruct
    public void init() {
        statePath = resolvePath(stateFile);
        try {
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare chat history directory", e);
        }
        loadState();
        // 启动时 MySQL 若可用，标记为已同步状态，避免首个 flush 冗余全量重插；
        // 后续运行时发生"不可用→恢复"转换时由 reconcileMysqlAfterReconnect 补齐。
        wasMysqlActive = mysqlStateStoreService.isActive();
        flushScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-history-flusher");
            thread.setDaemon(true);
            return thread;
        });
        flushScheduler.scheduleAtFixedRate(this::scheduledFlush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 定时任务入口：先对账（MySQL 从不可用恢复时补齐降级期间产生的本地记录），再消费增量批量。
     */
    private void scheduledFlush() {
        reconcileMysqlAfterReconnect();
        flushPending();
    }

    /**
     * MySQL 由不可用恢复为可用时，把本地完整历史快照全量同步到 MySQL（saveChatHistoryState 为
     * DELETE + 全量重插），补齐降级期间只落在本地 JSON 的记录。仅在转换瞬间执行一次。
     */
    private void reconcileMysqlAfterReconnect() {
        if (!mysqlStateStoreService.isActive()) {
            wasMysqlActive = false;
            return;
        }
        if (wasMysqlActive) {
            return;
        }
        wasMysqlActive = true;
        List<ChatRecord> snapshot;
        synchronized (records) {
            snapshot = new ArrayList<>(records);
        }
        try {
            mysqlStateStoreService.saveChatHistoryState(toMysqlState(new StateSnapshot(snapshot)));
            log.info("MySQL reconnected, synced {} chat history records to MySQL", snapshot.size());
        } catch (Exception e) {
            log.warn("MySQL reconnect reconciliation failed, will retry on next flush", e);
            wasMysqlActive = false;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (flushScheduler != null) {
            flushScheduler.shutdown();
            try {
                flushScheduler.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        flushPending();
    }

    /**
     * 保存聊天记录：只做内存追加并进入异步落盘队列，立即返回。
     * 相比旧的"每次请求同步全量写文件 + MySQL 全表重插"，请求线程不再阻塞在磁盘/网络 I/O。
     */
    public void save(String userId, String sessionId, String question, ChatResponse response) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        ChatRecord record = ChatRecord.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .sessionId(sessionId)
                .question(question)
                .answer(response.getAnswer())
                .sources(response.getSources() == null ? List.of() : response.getSources())
                .knowledgeGraph(response.getKnowledgeGraph() == null ? Map.of() : response.getKnowledgeGraph())
                .createdAt(OffsetDateTime.now().toString())
                .build();

        synchronized (records) {
            records.add(record);
            pending.add(record);
        }
    }

    /** 后台线程定期调用：把增量批量落盘（本地快照 + MySQL 增量 INSERT）。 */
    private void flushPending() {
        List<ChatRecord> batch;
        synchronized (records) {
            if (pending.isEmpty()) {
                return;
            }
            batch = new ArrayList<>(pending);
            pending.clear();
        }
        try {
            persistLocalState();
            if (mysqlStateStoreService.isActive()) {
                mysqlStateStoreService.appendChatMessages(toMysqlState(new StateSnapshot(batch)).getRecords());
            }
        } catch (Exception e) {
            log.warn("Failed to flush {} chat history records", batch.size(), e);
            synchronized (records) {
                pending.addAll(0, batch);
            }
        }
    }

    public PageResponse<ChatHistoryItem> getHistory(String userId, String sessionId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);

        List<ChatHistoryItem> filtered;
        synchronized (records) {
            filtered = records.stream()
                    .filter(record -> record.getUserId().equals(userId))
                    .filter(record -> sessionId == null || sessionId.isBlank() || record.getSessionId().equals(sessionId))
                    .sorted(Comparator.comparing(ChatRecord::getCreatedAt).reversed())
                    .map(this::toHistoryItem)
                    .toList();
        }

        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return PageResponse.<ChatHistoryItem>builder()
                .total(filtered.size())
                .page(safePage)
                .size(safeSize)
                .items(filtered.subList(fromIndex, toIndex))
                .build();
    }

    public PageResponse<ChatSessionItem> getSessions(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);

        Map<String, List<ChatRecord>> grouped = new LinkedHashMap<>();
        synchronized (records) {
            records.stream()
                    .filter(record -> record.getUserId().equals(userId))
                    .sorted(Comparator.comparing(ChatRecord::getCreatedAt))
                    .forEach(record -> grouped.computeIfAbsent(record.getSessionId(), key -> new ArrayList<>()).add(record));
        }

        List<ChatSessionItem> sessions = grouped.entrySet().stream()
                .map(entry -> {
                    List<ChatRecord> items = entry.getValue();
                    ChatRecord first = items.get(0);
                    ChatRecord last = items.get(items.size() - 1);
                    String title = first.getQuestion() == null ? "未命名会话" : truncate(first.getQuestion(), 24);
                    return ChatSessionItem.builder()
                            .sessionId(entry.getKey())
                            .title(title)
                            .lastMessage(last.getQuestion())
                            .updatedAt(last.getCreatedAt())
                            .messageCount(items.size())
                            .build();
                })
                .sorted(Comparator.comparing(ChatSessionItem::getUpdatedAt).reversed())
                .toList();

        int fromIndex = Math.min((safePage - 1) * safeSize, sessions.size());
        int toIndex = Math.min(fromIndex + safeSize, sessions.size());
        return PageResponse.<ChatSessionItem>builder()
                .total(sessions.size())
                .page(safePage)
                .size(safeSize)
                .items(sessions.subList(fromIndex, toIndex))
                .build();
    }

    public List<ChatHistoryItem> getAllHistoryForEvaluation() {
        synchronized (records) {
            return records.stream()
                    .sorted(Comparator.comparing(ChatRecord::getCreatedAt).reversed())
                    .map(this::toHistoryItem)
                    .toList();
        }
    }

    private ChatHistoryItem toHistoryItem(ChatRecord record) {
        return ChatHistoryItem.builder()
                .id(record.getId())
                .sessionId(record.getSessionId())
                .question(record.getQuestion())
                .answer(record.getAnswer())
                .sources(record.getSources())
                .knowledgeGraph(record.getKnowledgeGraph())
                .createdAt(record.getCreatedAt())
                .build();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private void loadState() {
        if (Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), StateSnapshot.class));
            } catch (IOException e) {
                log.warn("Failed to load chat history state, starting fresh", e);
                records.clear();
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            return;
        }

        java.util.Optional<MysqlStateStoreService.ChatHistoryStateData> mysqlState = mysqlStateStoreService.loadChatHistoryState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            return;
        }

        if (!records.isEmpty()) {
            mysqlStateStoreService.saveChatHistoryState(
                    toMysqlState(new StateSnapshot(new ArrayList<>(records)))
            );
        }
    }

    /**
     * 全量落盘：锁内只做记录引用快照（微秒级），JSON 序列化 + 文件写入在锁外执行。
     * 若在锁内做全量序列化写盘，flush 线程会长时间独占 records 锁，
     * 阻塞所有请求线程的 save()/getHistory()，高并发下形成雪崩。
     */
    private void persistLocalState() {
        List<ChatRecord> snapshot;
        synchronized (records) {
            snapshot = new ArrayList<>(records);
        }
        persistLocalState(new StateSnapshot(snapshot));
    }

    private void persistLocalState(StateSnapshot snapshot) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(statePath.toFile(), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist chat history state", e);
        }
    }

    private void applyState(StateSnapshot snapshot) {
        records.clear();
        if (snapshot != null && snapshot.getRecords() != null) {
            records.addAll(snapshot.getRecords());
        }
    }

    private MysqlStateStoreService.ChatHistoryStateData toMysqlState(StateSnapshot snapshot) {
        List<MysqlStateStoreService.ChatMessageData> mysqlRecords = snapshot.getRecords() == null
                ? List.of()
                : snapshot.getRecords().stream()
                .map(record -> new MysqlStateStoreService.ChatMessageData(
                        record.getId(),
                        record.getUserId(),
                        record.getSessionId(),
                        record.getQuestion(),
                        record.getAnswer(),
                        record.getSources() == null ? List.of() : record.getSources(),
                        record.getKnowledgeGraph() == null ? Map.of() : record.getKnowledgeGraph(),
                        record.getCreatedAt()
                ))
                .toList();
        return new MysqlStateStoreService.ChatHistoryStateData(mysqlRecords);
    }

    private StateSnapshot fromMysqlState(MysqlStateStoreService.ChatHistoryStateData state) {
        List<ChatRecord> mysqlRecords = state.getRecords() == null
                ? List.of()
                : state.getRecords().stream()
                .map(record -> ChatRecord.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                        .sessionId(record.getSessionId())
                        .question(record.getQuestion())
                        .answer(record.getAnswer())
                        .sources(record.getSources())
                        .knowledgeGraph(record.getKnowledgeGraph())
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
        Path applicationDir = new ApplicationHome(ChatHistoryService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ChatRecord {
        private String id;
        private String userId;
        private String sessionId;
        private String question;
        private String answer;
        private List<ChatResponse.Source> sources;
        private Map<String, Object> knowledgeGraph;
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class StateSnapshot {
        private List<ChatRecord> records = new ArrayList<>();
    }
}
