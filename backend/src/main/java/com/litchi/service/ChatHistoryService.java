package com.litchi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatHistoryItem;
import com.litchi.dto.ChatResponse;
import com.litchi.dto.ChatSessionItem;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {
    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;

    @Value("${app.chat-history.state-file:data/chat-history.json}")
    private String stateFile;

    private final List<ChatRecord> records = new ArrayList<>();
    private Path statePath;

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
    }

    public synchronized void save(String userId, String sessionId, String question, ChatResponse response) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }

        records.add(ChatRecord.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .userId(userId)
                .sessionId(sessionId)
                .question(question)
                .answer(response.getAnswer())
                .sources(response.getSources() == null ? List.of() : response.getSources())
                .knowledgeGraph(response.getKnowledgeGraph() == null ? Map.of() : response.getKnowledgeGraph())
                .createdAt(OffsetDateTime.now().toString())
                .build());
        persistState();
    }

    public synchronized PageResponse<ChatHistoryItem> getHistory(String userId, String sessionId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);

        List<ChatHistoryItem> filtered = records.stream()
                .filter(record -> record.getUserId().equals(userId))
                .filter(record -> sessionId == null || sessionId.isBlank() || record.getSessionId().equals(sessionId))
                .sorted(Comparator.comparing(ChatRecord::getCreatedAt).reversed())
                .map(this::toHistoryItem)
                .toList();

        int fromIndex = Math.min((safePage - 1) * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        return PageResponse.<ChatHistoryItem>builder()
                .total(filtered.size())
                .page(safePage)
                .size(safeSize)
                .items(filtered.subList(fromIndex, toIndex))
                .build();
    }

    public synchronized PageResponse<ChatSessionItem> getSessions(String userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);

        Map<String, List<ChatRecord>> grouped = new LinkedHashMap<>();
        records.stream()
                .filter(record -> record.getUserId().equals(userId))
                .sorted(Comparator.comparing(ChatRecord::getCreatedAt))
                .forEach(record -> grouped.computeIfAbsent(record.getSessionId(), key -> new ArrayList<>()).add(record));

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

    private void persistState() {
        StateSnapshot snapshot = new StateSnapshot(new ArrayList<>(records));
        persistLocalState(snapshot);
        mysqlStateStoreService.saveChatHistoryState(toMysqlState(snapshot));
    }

    private void persistLocalState() {
        persistLocalState(new StateSnapshot(new ArrayList<>(records)));
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
