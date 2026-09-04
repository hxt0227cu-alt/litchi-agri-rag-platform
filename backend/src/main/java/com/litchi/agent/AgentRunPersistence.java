package com.litchi.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.AgentRunResponse;
import com.litchi.service.MysqlStateStoreService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRunPersistence {
    private final MysqlStateStoreService mysqlStateStoreService;
    private final AgentRunStore runStore;
    private final ObjectMapper objectMapper;

    /** 记录上一次检查时 MySQL 是否可用，用于检测"不可用→恢复"转换并对账回填。 */
    private volatile boolean wasMysqlActive;

    /** 超过该时长未更新的非终态运行视为失去推进者（实例崩溃/重启），恢复扫描将其标记为 interrupted。 */
    @Value("${app.agent.recover-stale-seconds:120}")
    private long recoverStaleSeconds = 120;

    @PostConstruct
    public void init() {
        wasMysqlActive = mysqlStateStoreService.isActive();
        ScheduledExecutorService reconciler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-run-reconciler");
            thread.setDaemon(true);
            return thread;
        });
        reconciler.scheduleAtFixedRate(this::reconcileAfterReconnect, 5, 10, TimeUnit.SECONDS);
        ScheduledExecutorService recoverer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "agent-run-recoverer");
            thread.setDaemon(true);
            return thread;
        });
        recoverer.scheduleAtFixedRate(this::recoverStaleRuns, 15, 60, TimeUnit.SECONDS);
    }

    /**
     * 跨实例恢复扫描：将长期停留在 created/planning/running 且超过阈值未推进的运行标记为 interrupted。
     * 不自动续跑——工具执行不保证幂等，自动恢复已执行的写步骤可能造成重复副作用；
     * 标记为终态让运行"诚实落地"，用户可基于结果重新提交。等待审批的运行不在此列，跨实例仍可继续决策。
     */
    private void recoverStaleRuns() {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minusSeconds(Math.max(30, recoverStaleSeconds));
            int recovered = 0;
            for (MysqlStateStoreService.AgentRunRow row : mysqlStateStoreService.scanRecoverableAgentRuns()) {
                if (isStale(row.updatedAt(), cutoff)) {
                    markInterrupted(row);
                    recovered++;
                }
            }
            if (recovered > 0) {
                log.info("Agent recovery: marked {} stale run(s) as interrupted", recovered);
            }
        } catch (Exception exception) {
            log.warn("Agent recovery scan failed: {}", exception.getMessage());
        }
    }

    private void markInterrupted(MysqlStateStoreService.AgentRunRow row) {
        load(row.ownerId(), row.runId()).ifPresent(current -> {
            String answer = current.getAnswer() == null || current.getAnswer().isBlank()
                    ? "运行在实例中断后未完成，已被恢复机制标记为可重新提交。"
                    : current.getAnswer();
            AgentRunResponse interrupted = current.toBuilder()
                    .status("interrupted")
                    .answer(answer)
                    .build();
            save(row.ownerId(), interrupted);
            log.info("Agent recovery marked run {} as interrupted (was {})", row.runId(), row.statusName());
        });
    }

    private boolean isStale(String updatedAt, Instant cutoff) {
        if (updatedAt == null || updatedAt.isBlank()) {
            return true;
        }
        try {
            return Instant.parse(updatedAt).isBefore(cutoff);
        } catch (Exception exception) {
            return true;
        }
    }

    /**
     * MySQL 由不可用恢复为可用时，把内存运行存储中的全部 Agent 运行回填到 MySQL。
     * Agent 运行是"MySQL 优先 + 内存回退"，降级期间完成的运行只存在于内存，
     * 无此对账会在重连后永久缺失于 MySQL（与 ChatHistoryService 的聊天记录对账对齐）。
     */
    private void reconcileAfterReconnect() {
        if (!mysqlStateStoreService.isActive()) {
            wasMysqlActive = false;
            return;
        }
        if (wasMysqlActive) {
            return;
        }
        wasMysqlActive = true;
        int backfilled = 0;
        for (AgentRunStore.StoredRun storedRun : runStore.snapshot()) {
            try {
                save(storedRun.ownerId(), storedRun.response());
                backfilled++;
            } catch (Exception e) {
                log.warn("Agent run reconcile backfill failed for run {}", storedRun.response().getRunId(), e);
            }
        }
        log.info("MySQL reconnected, backfilled {} agent runs to MySQL", backfilled);
    }

    public void save(String ownerId, AgentRunResponse response) {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            mysqlStateStoreService.saveAgentRun(ownerId, objectMapper.writeValueAsString(response), response);
            if (isStructuredSyncPoint(response.getStatus())) {
                List<AgentRunResponse.Step> steps = response.getSteps() == null ? List.of() : response.getSteps();
                mysqlStateStoreService.syncAgentRunSteps(ownerId, response.getRunId(), steps,
                        response.getStartedAt() == null ? Instant.now().toString() : response.getStartedAt());
                if ("waiting_approval".equals(response.getStatus()) && response.getPendingAction() != null) {
                    mysqlStateStoreService.saveAgentRunApproval(
                            response.getRunId(),
                            ownerId,
                            String.valueOf(response.getCheckpoint().getOrDefault("pendingTool", "")),
                            objectMapper.writeValueAsString(response.getPendingAction()));
                }
            }
        } catch (Exception ignored) {
            // The in-memory run store remains the availability fallback.
        }
    }

    /**
     * 步骤/审批结构化表只在运行进入"可审阅快照"状态时同步一次，避免热路径高频写。
     */
    private boolean isStructuredSyncPoint(String status) {
        return "completed".equals(status) || "failed".equals(status) || "canceled".equals(status)
                || "refused".equals(status) || "interrupted".equals(status) || "waiting_approval".equals(status);
    }

    /** 记录审批决策（approve/reject）到审批表。 */
    public void decideApproval(String runId, String decision, String decidedBy) {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            mysqlStateStoreService.decideAgentRunApproval(runId, decision, decidedBy);
        } catch (Exception ignored) {
            // 决策记录失败不影响运行状态流转；可审计性通过出站事件与主表状态补偿。
        }
    }

    /** 按所有者列出持久化运行（重启/跨实例后可见性）。 */
    public List<AgentRunResponse> list(String ownerId, Integer limit, String status) {
        if (!mysqlStateStoreService.isActive()) {
            return List.of();
        }
        try {
            return mysqlStateStoreService.listAgentRuns(ownerId, limit, status);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public Optional<AgentRunResponse> load(String ownerId, String runId) {
        if (!mysqlStateStoreService.isActive()) {
            return Optional.empty();
        }
        try {
            return mysqlStateStoreService.loadAgentRun(ownerId, runId)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, AgentRunResponse.class);
                        } catch (Exception exception) {
                            return null;
                        }
                    });
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void publishTerminalEvent(AgentRunResponse response) {
        if (!mysqlStateStoreService.isActive()) {
            return;
        }
        try {
            String status = response.getStatus();
            String eventType = "agent.run." + status;
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> summary = Map.of(
                    "runId", response.getRunId(),
                    "status", response.getStatus(),
                    "goalLength", response.getGoal() == null ? 0 : response.getGoal().length(),
                    "durationMs", response.getDurationMs(),
                    "degraded", response.isDegraded(),
                    "riskLevel", response.getRiskLevel() == null ? "unknown" : response.getRiskLevel(),
                    "reviewRequired", response.isReviewRequired(),
                    "stepCount", response.getSteps() == null ? 0 : response.getSteps().size(),
                    "steps", response.getSteps() == null ? List.of() : response.getSteps().stream()
                            .map(step -> Map.of("tool", step.getTool(), "status", step.getStatus(), "durationMs", step.getDurationMs()))
                            .collect(Collectors.toList())
            );
            String eventJson = objectMapper.writeValueAsString(Map.of(
                    "eventId", eventId,
                    "eventType", eventType,
                    "schemaVersion", 1,
                    "tenantId", "tenant-default",
                    "occurredAt", Instant.now().toString(),
                    "traceId", response.getRunId(),
                    "payload", summary
            ));
            mysqlStateStoreService.saveOutboxEvent(
                    eventId,
                    eventType,
                    1,
                    "tenant-default",
                    response.getRunId(),
                    eventJson,
                    Instant.now().toString()
            );
        } catch (Exception ignored) {
            // Outbox failure is observable through the persistence health metric; request availability remains intact.
        }
    }
}
