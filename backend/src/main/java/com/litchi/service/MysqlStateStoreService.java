package com.litchi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatResponse;
import com.litchi.dto.EvaluationRubricScore;
import com.litchi.dto.AgentRunResponse;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MysqlStateStoreService {
    private static final String CREATE_USERS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_users (
                user_id VARCHAR(64) NOT NULL PRIMARY KEY,
                username VARCHAR(64) NOT NULL UNIQUE,
                password_hash VARCHAR(128) NOT NULL,
                password_salt VARCHAR(64) NOT NULL,
                role_name VARCHAR(32) NOT NULL,
                created_at VARCHAR(64) NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_SESSIONS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_sessions (
                token VARCHAR(64) NOT NULL PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                created_at VARCHAR(64) NOT NULL,
                expires_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_sessions_user_id (user_id),
                INDEX idx_platform_sessions_expires_at (expires_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_CHAT_MESSAGES_SQL = """
            CREATE TABLE IF NOT EXISTS platform_chat_messages (
                message_id VARCHAR(64) NOT NULL PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                session_id VARCHAR(64) NOT NULL,
                question LONGTEXT NOT NULL,
                answer LONGTEXT NOT NULL,
                sources_json LONGTEXT NULL,
                knowledge_graph_json LONGTEXT NULL,
                created_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_chat_user_session (user_id, session_id),
                INDEX idx_platform_chat_created_at (created_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_EVALUATIONS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_evaluations (
                evaluation_id BIGINT NOT NULL PRIMARY KEY,
                question_type VARCHAR(64) NOT NULL,
                question LONGTEXT NOT NULL,
                reference_answer LONGTEXT NOT NULL,
                system_answer LONGTEXT NULL,
                auto_score DOUBLE NULL,
                score_breakdown_json LONGTEXT NULL,
                bleu_score DOUBLE NULL,
                human_score INT NULL,
                review_note LONGTEXT NULL,
                review_status VARCHAR(32) NULL,
                evaluated BOOLEAN NOT NULL,
                created_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_evaluations_type (question_type),
                INDEX idx_platform_evaluations_evaluated (evaluated)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String ALTER_EVALUATIONS_ADD_AUTO_SCORE_SQL =
            "ALTER TABLE platform_evaluations ADD COLUMN auto_score DOUBLE NULL";
    private static final String ALTER_EVALUATIONS_ADD_SCORE_BREAKDOWN_SQL =
            "ALTER TABLE platform_evaluations ADD COLUMN score_breakdown_json LONGTEXT NULL";
    private static final String ALTER_EVALUATIONS_ADD_REVIEW_NOTE_SQL =
            "ALTER TABLE platform_evaluations ADD COLUMN review_note LONGTEXT NULL";
    private static final String ALTER_EVALUATIONS_ADD_REVIEW_STATUS_SQL =
            "ALTER TABLE platform_evaluations ADD COLUMN review_status VARCHAR(32) NULL";

    private static final String CREATE_DOCUMENTS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_documents (
                document_id VARCHAR(64) NOT NULL PRIMARY KEY,
                document_name VARCHAR(255) NOT NULL,
                document_title VARCHAR(255) NOT NULL,
                document_size BIGINT NOT NULL,
                content_type VARCHAR(255) NOT NULL,
                upload_time VARCHAR(64) NOT NULL,
                chunk_count INT NOT NULL,
                is_indexed BOOLEAN NOT NULL,
                status_message TEXT NULL,
                storage_path VARCHAR(512) NOT NULL,
                owner_id VARCHAR(64) NULL,
                owner_username VARCHAR(64) NULL,
                INDEX idx_platform_documents_upload_time (upload_time)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_DOCUMENT_CHUNKS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_document_chunks (
                chunk_id VARCHAR(128) NOT NULL PRIMARY KEY,
                document_id VARCHAR(64) NOT NULL,
                chunk_title VARCHAR(255) NOT NULL,
                chunk_source VARCHAR(255) NOT NULL,
                chunk_content LONGTEXT NOT NULL,
                page_number INT NULL,
                vector_json LONGTEXT NULL,
                INDEX idx_platform_document_chunks_document_id (document_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_STORE_PROFILES_SQL = """
            CREATE TABLE IF NOT EXISTS platform_store_profiles (
                shop_id VARCHAR(64) NOT NULL PRIMARY KEY,
                owner_id VARCHAR(64) NULL,
                owner_username VARCHAR(64) NULL,
                shop_name VARCHAR(255) NOT NULL,
                contact_name VARCHAR(255) NOT NULL,
                phone VARCHAR(64) NULL,
                wechat VARCHAR(128) NULL,
                address VARCHAR(255) NULL,
                service_area VARCHAR(255) NULL,
                specialties VARCHAR(255) NULL,
                rating DOUBLE NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_store_profiles_owner_username (owner_username)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_REMEDY_PLANS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_remedy_plans (
                plan_id VARCHAR(64) NOT NULL PRIMARY KEY,
                shop_id VARCHAR(64) NOT NULL,
                owner_id VARCHAR(64) NULL,
                owner_username VARCHAR(64) NULL,
                shop_name VARCHAR(255) NOT NULL,
                title VARCHAR(255) NOT NULL,
                disease_tag VARCHAR(128) NOT NULL,
                stage_tag VARCHAR(128) NOT NULL,
                summary LONGTEXT NOT NULL,
                products_json LONGTEXT NULL,
                usage_tips_json LONGTEXT NULL,
                risk_notes_json LONGTEXT NULL,
                inventory_status VARCHAR(64) NOT NULL,
                is_active BOOLEAN NOT NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                idempotency_key VARCHAR(64) NULL,
                INDEX idx_platform_remedy_plans_shop_id (shop_id),
                INDEX idx_platform_remedy_plans_disease_tag (disease_tag),
                INDEX idx_platform_remedy_plans_owner_username (owner_username),
                INDEX idx_platform_remedy_plans_idempotency_key (idempotency_key)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_CONSULTATIONS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_consultations (
                consultation_id VARCHAR(64) NOT NULL PRIMARY KEY,
                farmer_user_id VARCHAR(64) NOT NULL,
                farmer_username VARCHAR(64) NOT NULL,
                disease_tag VARCHAR(128) NOT NULL,
                stage_tag VARCHAR(128) NULL,
                question LONGTEXT NULL,
                plan_id VARCHAR(64) NOT NULL,
                plan_title VARCHAR(255) NOT NULL,
                shop_id VARCHAR(64) NOT NULL,
                shop_name VARCHAR(255) NOT NULL,
                status_name VARCHAR(32) NOT NULL,
                reason_tags_json LONGTEXT NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_consultations_shop_status (shop_id, status_name),
                INDEX idx_platform_consultations_farmer_user_id (farmer_user_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_FEEDBACK_RECORDS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_feedback_records (
                feedback_id VARCHAR(64) NOT NULL PRIMARY KEY,
                user_id VARCHAR(64) NOT NULL,
                username VARCHAR(64) NOT NULL,
                role_name VARCHAR(32) NOT NULL,
                module_name VARCHAR(128) NOT NULL,
                overall_score INT NOT NULL,
                accuracy_score INT NOT NULL,
                practicality_score INT NOT NULL,
                fluency_score INT NOT NULL,
                comment LONGTEXT NULL,
                created_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_feedback_module_name (module_name),
                INDEX idx_platform_feedback_created_at (created_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_AGENT_RUNS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_agent_runs (
                run_id VARCHAR(64) NOT NULL PRIMARY KEY,
                owner_id VARCHAR(64) NOT NULL,
                tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default',
                status_name VARCHAR(32) NOT NULL,
                goal LONGTEXT NOT NULL,
                payload_json LONGTEXT NOT NULL,
                started_at VARCHAR(64) NULL,
                duration_ms BIGINT NOT NULL DEFAULT 0,
                degraded BOOLEAN NOT NULL DEFAULT FALSE,
                risk_level VARCHAR(16) NULL,
                review_required BOOLEAN NOT NULL DEFAULT FALSE,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_agent_runs_owner_updated (owner_id, updated_at),
                INDEX idx_platform_agent_runs_status_updated (status_name, updated_at),
                INDEX idx_platform_agent_runs_tenant_updated (tenant_id, updated_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    /**
     * 步骤表：每次 Agent 运行执行的结构化步骤轨迹，与主表 1:N。
     * 热路径（chat/agent 高频保存）不写本表，仅在运行进入终态或等待审批时整体替换一次。
     */
    private static final String CREATE_AGENT_STEPS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_agent_steps (
                step_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                run_id VARCHAR(64) NOT NULL,
                owner_id VARCHAR(64) NOT NULL,
                sequence_no INT NOT NULL,
                tool_name VARCHAR(64) NOT NULL,
                reason_text VARCHAR(512) NULL,
                status_name VARCHAR(32) NOT NULL,
                duration_ms BIGINT NOT NULL DEFAULT 0,
                output_json LONGTEXT NULL,
                error_message VARCHAR(512) NULL,
                created_at VARCHAR(64) NOT NULL,
                UNIQUE KEY uk_agent_steps_run_seq (run_id, sequence_no),
                INDEX idx_agent_steps_run (run_id)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    /**
     * 审批表：等待人工审批的运行，每 run 一行（pending/approved/rejected），跨实例可见、可继续决策。
     */
    private static final String CREATE_AGENT_APPROVALS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_agent_approvals (
                approval_id VARCHAR(64) NOT NULL PRIMARY KEY,
                run_id VARCHAR(64) NOT NULL,
                owner_id VARCHAR(64) NOT NULL,
                tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default',
                pending_tool VARCHAR(64) NOT NULL,
                action_json LONGTEXT NOT NULL,
                decision_name VARCHAR(16) NOT NULL DEFAULT 'pending',
                decided_by VARCHAR(64) NULL,
                decided_at VARCHAR(64) NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                UNIQUE KEY uk_agent_approvals_run (run_id),
                INDEX idx_agent_approvals_pending (decision_name, updated_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    /** 存量 platform_agent_runs 表补充结构化列（幂等，重复列自动跳过）。 */
    private static final String[] AGENT_COLUMN_MIGRATIONS = {
            "ALTER TABLE platform_agent_runs ADD COLUMN started_at VARCHAR(64) NULL",
            "ALTER TABLE platform_agent_runs ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0",
            "ALTER TABLE platform_agent_runs ADD COLUMN degraded BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE platform_agent_runs ADD COLUMN risk_level VARCHAR(16) NULL",
            "ALTER TABLE platform_agent_runs ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE"
    };

    private static final String CREATE_TENANTS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_tenants (
                tenant_id VARCHAR(64) NOT NULL PRIMARY KEY,
                tenant_name VARCHAR(255) NOT NULL,
                tenant_type VARCHAR(64) NOT NULL,
                status_name VARCHAR(32) NOT NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_ORCHARDS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_orchards (
                orchard_id VARCHAR(64) NOT NULL PRIMARY KEY,
                tenant_id VARCHAR(64) NOT NULL,
                owner_id VARCHAR(64) NOT NULL,
                orchard_name VARCHAR(128) NOT NULL,
                location VARCHAR(255) NULL,
                variety_name VARCHAR(64) NULL,
                growth_stage VARCHAR(64) NULL,
                area_mu DECIMAL(12,2) NULL,
                created_at VARCHAR(64) NOT NULL,
                updated_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_orchards_owner_updated (owner_id, updated_at),
                INDEX idx_platform_orchards_tenant_updated (tenant_id, updated_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String CREATE_OUTBOX_EVENTS_SQL = """
            CREATE TABLE IF NOT EXISTS platform_outbox_events (
                event_id VARCHAR(64) NOT NULL PRIMARY KEY,
                event_type VARCHAR(128) NOT NULL,
                schema_version INT NOT NULL,
                tenant_id VARCHAR(64) NOT NULL,
                aggregate_id VARCHAR(64) NOT NULL,
                payload_json LONGTEXT NOT NULL,
                occurred_at VARCHAR(64) NOT NULL,
                published_at VARCHAR(64) NULL,
                INDEX idx_platform_outbox_unpublished (published_at, occurred_at),
                INDEX idx_platform_outbox_tenant_event (tenant_id, event_type, occurred_at)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

    private static final String[] TENANT_COLUMN_MIGRATIONS = {
            "ALTER TABLE platform_users ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'",
            "ALTER TABLE platform_chat_messages ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'",
            "ALTER TABLE platform_documents ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'",
            "ALTER TABLE platform_remedy_plans ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'",
            "ALTER TABLE platform_consultations ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'",
            "ALTER TABLE platform_feedback_records ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT 'tenant-default'"
    };

    private static final String[] IDEMPOTENCY_COLUMN_MIGRATIONS = {
            "ALTER TABLE platform_remedy_plans ADD COLUMN idempotency_key VARCHAR(64) NULL",
            "ALTER TABLE platform_remedy_plans ADD INDEX idx_platform_remedy_plans_idempotency_key (idempotency_key)"
    };

    private static final String SELECT_USERS_SQL = """
            SELECT user_id, username, password_hash, password_salt, role_name, created_at
            FROM platform_users
            ORDER BY created_at ASC, user_id ASC
            """;

    private static final String SELECT_SESSIONS_SQL = """
            SELECT token, user_id, created_at, expires_at
            FROM platform_sessions
            ORDER BY created_at ASC, token ASC
            """;

    private static final String SELECT_CHAT_MESSAGES_SQL = """
            SELECT message_id, user_id, session_id, question, answer, sources_json, knowledge_graph_json, created_at
            FROM platform_chat_messages
            ORDER BY created_at ASC, message_id ASC
            """;

    private static final String SELECT_EVALUATIONS_SQL = """
            SELECT evaluation_id, question_type, question, reference_answer, system_answer, auto_score, score_breakdown_json,
                   bleu_score, human_score, review_note, review_status, evaluated, created_at
            FROM platform_evaluations
            ORDER BY evaluation_id ASC
            """;

    private static final String SELECT_DOCUMENTS_SQL = """
            SELECT document_id, document_name, document_title, document_size, content_type, upload_time, chunk_count, is_indexed,
                   status_message, storage_path, owner_id, owner_username
            FROM platform_documents
            ORDER BY upload_time ASC, document_id ASC
            """;

    private static final String SELECT_DOCUMENT_CHUNKS_SQL = """
            SELECT chunk_id, document_id, chunk_title, chunk_source, chunk_content, page_number, vector_json
            FROM platform_document_chunks
            ORDER BY document_id ASC, chunk_id ASC
            """;

    private static final String SELECT_STORE_PROFILES_SQL = """
            SELECT shop_id, owner_id, owner_username, shop_name, contact_name, phone, wechat, address,
                   service_area, specialties, rating, created_at, updated_at
            FROM platform_store_profiles
            ORDER BY created_at ASC, shop_id ASC
            """;

    private static final String SELECT_REMEDY_PLANS_SQL = """
            SELECT plan_id, shop_id, owner_id, owner_username, shop_name, title, disease_tag, stage_tag, summary,
                   products_json, usage_tips_json, risk_notes_json, inventory_status, is_active, created_at, updated_at,
                   idempotency_key
            FROM platform_remedy_plans
            ORDER BY updated_at ASC, plan_id ASC
            """;

    private static final String SELECT_CONSULTATIONS_SQL = """
            SELECT consultation_id, farmer_user_id, farmer_username, disease_tag, stage_tag, question, plan_id,
                   plan_title, shop_id, shop_name, status_name, reason_tags_json, created_at, updated_at
            FROM platform_consultations
            ORDER BY updated_at ASC, consultation_id ASC
            """;

    private static final String SELECT_FEEDBACK_RECORDS_SQL = """
            SELECT feedback_id, user_id, username, role_name, module_name, overall_score, accuracy_score,
                   practicality_score, fluency_score, comment, created_at
            FROM platform_feedback_records
            ORDER BY created_at ASC, feedback_id ASC
            """;

    private static final TypeReference<List<ChatResponse.Source>> SOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final long RECONNECT_INTERVAL_MS = 15_000L;

    private final ObjectMapper objectMapper;

    @Value("${app.mysql.enabled:false}")
    private boolean enabled;

    @Value("${app.mysql.url:}")
    private String url;

    @Value("${app.mysql.username:}")
    private String username;

    @Value("${app.mysql.password:}")
    private String password;

    private volatile boolean active;

    private volatile ScheduledExecutorService reconnectScheduler;

    @PostConstruct
    public void init() {
        if (!enabled || url == null || url.isBlank()) {
            log.info("MySQL structured storage is disabled, falling back to local state files");
            active = false;
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            ensureSchema();
            active = true;
            log.info("MySQL structured storage is enabled: {}", sanitizeUrl(url));
        } catch (Exception e) {
            active = false;
            log.warn("Failed to initialize MySQL structured storage, local state files will stay active", e);
            scheduleReconnect();
        }
    }

    /**
     * WSL localhost forwarding is flaky in the dev environment, so a one-shot
     * init() that permanently degrades on a transient failure is not acceptable.
     * A daemon periodically retries schema readiness and flips active back on.
     */
    private void scheduleReconnect() {
        if (reconnectScheduler != null) {
            return;
        }
        synchronized (this) {
            if (reconnectScheduler != null) {
                return;
            }
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mysql-reconnector");
                thread.setDaemon(true);
                return thread;
            });
            reconnectScheduler.scheduleWithFixedDelay(
                    this::tryReconnect, RECONNECT_INTERVAL_MS, RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
            log.info("MySQL reconnect scheduler started (every {} ms)", RECONNECT_INTERVAL_MS);
        }
    }

    private void tryReconnect() {
        if (active || !enabled || url == null || url.isBlank()) {
            return;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            ensureSchema();
            active = true;
            log.info("MySQL structured storage reconnected: {}", sanitizeUrl(url));
        } catch (Exception e) {
            log.debug("MySQL reconnect attempt failed, will retry later", e);
        }
    }

    public boolean isActive() {
        return active;
    }

    public synchronized void saveAgentRun(String ownerId, String payloadJson, AgentRunResponse response) {
        if (!active) {
            return;
        }
        String now = response.getStartedAt() == null ? java.time.Instant.now().toString() : response.getStartedAt();
        String sql = """
                INSERT INTO platform_agent_runs (run_id, owner_id, tenant_id, status_name, goal, payload_json,
                    started_at, duration_ms, degraded, risk_level, review_required, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE status_name = VALUES(status_name), payload_json = VALUES(payload_json),
                    started_at = VALUES(started_at), duration_ms = VALUES(duration_ms), degraded = VALUES(degraded),
                    risk_level = VALUES(risk_level), review_required = VALUES(review_required), updated_at = VALUES(updated_at)
                """;
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, response.getRunId());
            statement.setString(2, ownerId);
            statement.setString(3, "tenant-default");
            statement.setString(4, response.getStatus());
            statement.setString(5, response.getGoal());
            statement.setString(6, payloadJson);
            statement.setString(7, now);
            statement.setLong(8, response.getDurationMs());
            statement.setBoolean(9, response.isDegraded());
            statement.setString(10, response.getRiskLevel());
            statement.setBoolean(11, response.isReviewRequired());
            statement.setString(12, now);
            statement.setString(13, java.time.Instant.now().toString());
            statement.executeUpdate();
        } catch (Exception exception) {
            deactivate(exception, "save agent run");
        }
    }

    /**
     * 整体替换某运行的步骤轨迹（终态/等待审批时调用，步骤数 ≤ 4，低频）。
     * 不做逐条增量更新，避免热路径多一次往返；正确性由"整体替换"保证。
     */
    public synchronized void syncAgentRunSteps(String ownerId, String runId, java.util.List<AgentRunResponse.Step> steps, String updatedAt) {
        if (!active) {
            return;
        }
        try (Connection connection = openConnection()) {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM platform_agent_steps WHERE run_id = ?")) {
                delete.setString(1, runId);
                delete.executeUpdate();
            }
            String insertSql = """
                    INSERT INTO platform_agent_steps (run_id, owner_id, sequence_no, tool_name, reason_text,
                        status_name, duration_ms, output_json, error_message, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            for (AgentRunResponse.Step step : steps) {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setString(1, runId);
                    insert.setString(2, ownerId);
                    insert.setInt(3, step.getSequence());
                    insert.setString(4, step.getTool());
                    insert.setString(5, step.getReason());
                    insert.setString(6, step.getStatus());
                    insert.setLong(7, step.getDurationMs());
                    insert.setString(8, step.getOutput() == null ? null : objectMapper.writeValueAsString(step.getOutput()));
                    insert.setString(9, step.getError());
                    insert.setString(10, updatedAt);
                    insert.executeUpdate();
                }
            }
        } catch (Exception exception) {
            deactivate(exception, "sync agent steps");
        }
    }

    /** 登记等待审批动作（幂等，按 run 一行 upsert，保持 pending）。 */
    public synchronized void saveAgentRunApproval(String runId, String ownerId, String pendingTool, String actionJson) {
        if (!active) {
            return;
        }
        String now = java.time.Instant.now().toString();
        String sql = """
                INSERT INTO platform_agent_approvals (approval_id, run_id, owner_id, tenant_id, pending_tool, action_json,
                    decision_name, decided_by, decided_at, created_at, updated_at)
                VALUES (?, ?, ?, 'tenant-default', ?, ?, 'pending', NULL, NULL, ?, ?)
                ON DUPLICATE KEY UPDATE pending_tool = VALUES(pending_tool), action_json = VALUES(action_json),
                    decision_name = 'pending', decided_by = NULL, decided_at = NULL, updated_at = VALUES(updated_at)
                """;
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "apr_" + runId);
            statement.setString(2, runId);
            statement.setString(3, ownerId);
            statement.setString(4, pendingTool);
            statement.setString(5, actionJson);
            statement.setString(6, now);
            statement.setString(7, now);
            statement.executeUpdate();
        } catch (Exception exception) {
            deactivate(exception, "save agent approval");
        }
    }

    /** 记录审批决策（approve/reject），供审计与跨实例决策追踪。 */
    public synchronized void decideAgentRunApproval(String runId, String decision, String decidedBy) {
        if (!active) {
            return;
        }
        String now = java.time.Instant.now().toString();
        String sql = "UPDATE platform_agent_approvals SET decision_name = ?, decided_by = ?, decided_at = ?, updated_at = ? WHERE run_id = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, decision);
            statement.setString(2, decidedBy);
            statement.setString(3, now);
            statement.setString(4, now);
            statement.setString(5, runId);
            statement.executeUpdate();
        } catch (Exception exception) {
            deactivate(exception, "decide agent approval");
        }
    }

    /** 按所有者列出运行（按更新时间倒序），供重启/跨实例后的可见性与恢复使用。 */
    public synchronized java.util.List<AgentRunResponse> listAgentRuns(String ownerId, Integer limit, String statusName) {
        if (!active) {
            return List.of();
        }
        String sql = "SELECT run_id, payload_json FROM platform_agent_runs WHERE owner_id = ?"
                + (statusName != null && !statusName.isBlank() ? " AND status_name = ?" : "")
                + " ORDER BY updated_at DESC LIMIT ?";
        java.util.List<AgentRunResponse> result = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, ownerId);
            if (statusName != null && !statusName.isBlank()) {
                statement.setString(index++, statusName);
            }
            statement.setInt(index, limit == null ? 50 : Math.max(1, Math.min(limit, 200)));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        AgentRunResponse response = objectMapper.readValue(resultSet.getString("payload_json"), AgentRunResponse.class);
                        if (response != null) {
                            result.add(response);
                        }
                    } catch (Exception ignored) {
                        // 单行反序列化失败只跳过该行，不影响列表整体。
                    }
                }
            }
        } catch (Exception exception) {
            deactivate(exception, "list agent runs");
        }
        return result;
    }

    /** 扫描非终态且可能已失去推进者的运行（created/planning/running），用于跨实例恢复标记。 */
    public synchronized java.util.List<AgentRunRow> scanRecoverableAgentRuns() {
        if (!active) {
            return List.of();
        }
        String sql = "SELECT run_id, owner_id, status_name, updated_at FROM platform_agent_runs "
                + "WHERE status_name IN ('created', 'planning', 'running')";
        java.util.List<AgentRunRow> result = new ArrayList<>();
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(new AgentRunRow(
                        resultSet.getString("run_id"),
                        resultSet.getString("owner_id"),
                        resultSet.getString("status_name"),
                        resultSet.getString("updated_at")
                ));
            }
        } catch (Exception exception) {
            deactivate(exception, "scan recoverable agent runs");
        }
        return result;
    }

    /** 运行行快照（恢复扫描用）。 */
    public record AgentRunRow(String runId, String ownerId, String statusName, String updatedAt) {
    }

    public synchronized Optional<String> loadAgentRun(String ownerId, String runId) {
        if (!active) {
            return Optional.empty();
        }
        String sql = "SELECT payload_json FROM platform_agent_runs WHERE run_id = ? AND owner_id = ?";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, ownerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(resultSet.getString(1)) : Optional.empty();
            }
        } catch (Exception exception) {
            deactivate(exception, "load agent run");
            return Optional.empty();
        }
    }

    public synchronized void saveOrchard(com.litchi.dto.OrchardResponse orchard) {
        if (!active) {
            return;
        }
        String sql = """
                INSERT INTO platform_orchards (orchard_id, tenant_id, owner_id, orchard_name, location, variety_name,
                    growth_stage, area_mu, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE orchard_name = VALUES(orchard_name), location = VALUES(location),
                    variety_name = VALUES(variety_name), growth_stage = VALUES(growth_stage), area_mu = VALUES(area_mu), updated_at = VALUES(updated_at)
                """;
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orchard.getId());
            statement.setString(2, orchard.getTenantId());
            statement.setString(3, orchard.getOwnerId());
            statement.setString(4, orchard.getName());
            statement.setString(5, orchard.getLocation());
            statement.setString(6, orchard.getVariety());
            statement.setString(7, orchard.getGrowthStage());
            if (orchard.getAreaMu() == null) statement.setNull(8, Types.DECIMAL); else statement.setBigDecimal(8, orchard.getAreaMu());
            statement.setString(9, orchard.getCreatedAt());
            statement.setString(10, orchard.getUpdatedAt());
            statement.executeUpdate();
        } catch (Exception exception) {
            deactivate(exception, "save orchard");
        }
    }

    public synchronized List<OrchardData> loadOrchards(String ownerId) {
        List<OrchardData> result = new ArrayList<>();
        if (!active) {
            return result;
        }
        String sql = "SELECT orchard_id, tenant_id, owner_id, orchard_name, location, variety_name, growth_stage, area_mu, created_at, updated_at FROM platform_orchards WHERE owner_id = ? ORDER BY updated_at DESC";
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrchardData(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getString(9), rs.getString(10)));
                }
            }
        } catch (Exception exception) {
            deactivate(exception, "load orchards");
        }
        return result;
    }

    public synchronized void saveOutboxEvent(
            String eventId,
            String eventType,
            int schemaVersion,
            String tenantId,
            String aggregateId,
            String payloadJson,
            String occurredAt
    ) {
        if (!active) {
            return;
        }
        String sql = """
                INSERT IGNORE INTO platform_outbox_events
                    (event_id, event_type, schema_version, tenant_id, aggregate_id, payload_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setString(2, eventType);
            statement.setInt(3, schemaVersion);
            statement.setString(4, tenantId);
            statement.setString(5, aggregateId);
            statement.setString(6, payloadJson);
            statement.setString(7, occurredAt);
            statement.executeUpdate();
        } catch (Exception exception) {
            deactivate(exception, "save outbox event");
        }
    }

    public synchronized Optional<AuthStateData> loadAuthState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection()) {
            List<AuthUserData> users = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_USERS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    users.add(new AuthUserData(
                            resultSet.getString("user_id"),
                            resultSet.getString("username"),
                            resultSet.getString("password_hash"),
                            resultSet.getString("password_salt"),
                            resultSet.getString("role_name"),
                            resultSet.getString("created_at")
                    ));
                }
            }

            List<AuthSessionData> sessions = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_SESSIONS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(new AuthSessionData(
                            resultSet.getString("token"),
                            resultSet.getString("user_id"),
                            resultSet.getString("created_at"),
                            resultSet.getString("expires_at")
                    ));
                }
            }

            if (users.isEmpty() && sessions.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new AuthStateData(users, sessions));
        } catch (Exception e) {
            deactivate(e, "load auth state");
            return Optional.empty();
        }
    }

    public synchronized void saveAuthState(AuthStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_sessions");
                executeUpdate(connection, "DELETE FROM platform_users");

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_users (user_id, username, password_hash, password_salt, role_name, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    for (AuthUserData user : safeList(state.getUsers())) {
                        statement.setString(1, user.getId());
                        statement.setString(2, user.getUsername());
                        statement.setString(3, user.getPasswordHash());
                        statement.setString(4, user.getPasswordSalt());
                        statement.setString(5, user.getRole());
                        statement.setString(6, user.getCreatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_sessions (token, user_id, created_at, expires_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    for (AuthSessionData session : safeList(state.getSessions())) {
                        statement.setString(1, session.getToken());
                        statement.setString(2, session.getUserId());
                        statement.setString(3, session.getCreatedAt());
                        statement.setString(4, session.getExpiresAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save auth state");
        }
    }

    public synchronized Optional<ChatHistoryStateData> loadChatHistoryState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_CHAT_MESSAGES_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            List<ChatMessageData> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(new ChatMessageData(
                        resultSet.getString("message_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("session_id"),
                        resultSet.getString("question"),
                        resultSet.getString("answer"),
                        readSources(resultSet.getString("sources_json")),
                        readMap(resultSet.getString("knowledge_graph_json")),
                        resultSet.getString("created_at")
                ));
            }
            return records.isEmpty() ? Optional.empty() : Optional.of(new ChatHistoryStateData(records));
        } catch (Exception e) {
            deactivate(e, "load chat history state");
            return Optional.empty();
        }
    }

    /**
     * 增量追加聊天记录（异步批量落盘使用）：不做全表删除，INSERT IGNORE 幂等。
     * 高并发下避免 saveChatHistoryState 的"DELETE + 全量重插"写放大。
     */
    public synchronized void appendChatMessages(List<ChatMessageData> messages) {
        if (!active || messages == null || messages.isEmpty()) {
            return;
        }
        String sql = """
                INSERT IGNORE INTO platform_chat_messages
                (message_id, user_id, session_id, question, answer, sources_json, knowledge_graph_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ChatMessageData record : messages) {
                statement.setString(1, record.getId());
                statement.setString(2, record.getUserId());
                statement.setString(3, record.getSessionId());
                statement.setString(4, record.getQuestion());
                statement.setString(5, record.getAnswer());
                statement.setString(6, writeJson(record.getSources()));
                statement.setString(7, writeJson(record.getKnowledgeGraph()));
                statement.setString(8, record.getCreatedAt());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception exception) {
            deactivate(exception, "append chat messages");
        }
    }

    public synchronized void saveChatHistoryState(ChatHistoryStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_chat_messages");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_chat_messages
                        (message_id, user_id, session_id, question, answer, sources_json, knowledge_graph_json, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (ChatMessageData record : safeList(state.getRecords())) {
                        statement.setString(1, record.getId());
                        statement.setString(2, record.getUserId());
                        statement.setString(3, record.getSessionId());
                        statement.setString(4, record.getQuestion());
                        statement.setString(5, record.getAnswer());
                        statement.setString(6, writeJson(record.getSources()));
                        statement.setString(7, writeJson(record.getKnowledgeGraph()));
                        statement.setString(8, record.getCreatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save chat history state");
        }
    }

    public synchronized Optional<EvaluationStateData> loadEvaluationState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_EVALUATIONS_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            List<EvaluationRecordData> records = new ArrayList<>();
            while (resultSet.next()) {
                Double autoScore = resultSet.getObject("auto_score") == null ? null : resultSet.getDouble("auto_score");
                Double bleuScore = resultSet.getObject("bleu_score") == null ? null : resultSet.getDouble("bleu_score");
                Integer humanScore = resultSet.getObject("human_score") == null ? null : resultSet.getInt("human_score");
                records.add(new EvaluationRecordData(
                        resultSet.getLong("evaluation_id"),
                        resultSet.getString("question_type"),
                        resultSet.getString("question"),
                        resultSet.getString("reference_answer"),
                        resultSet.getString("system_answer"),
                        autoScore,
                        readRubricScore(resultSet.getString("score_breakdown_json")),
                        bleuScore,
                        humanScore,
                        resultSet.getString("review_note"),
                        resultSet.getString("review_status"),
                        resultSet.getBoolean("evaluated"),
                        resultSet.getString("created_at")
                ));
            }
            return records.isEmpty() ? Optional.empty() : Optional.of(new EvaluationStateData(records));
        } catch (Exception e) {
            deactivate(e, "load evaluation state");
            return Optional.empty();
        }
    }

    public synchronized void saveEvaluationState(EvaluationStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_evaluations");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_evaluations
                        (evaluation_id, question_type, question, reference_answer, system_answer, auto_score, score_breakdown_json,
                         bleu_score, human_score, review_note, review_status, evaluated, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (EvaluationRecordData record : safeList(state.getRecords())) {
                        statement.setLong(1, record.getId());
                        statement.setString(2, record.getType());
                        statement.setString(3, record.getQuestion());
                        statement.setString(4, record.getReferenceAnswer());
                        statement.setString(5, record.getSystemAnswer());
                        setNullableDouble(statement, 6, record.getAutoScore());
                        statement.setString(7, writeJson(record.getScoreBreakdown()));
                        setNullableDouble(statement, 8, record.getBleuScore());
                        setNullableInteger(statement, 9, record.getHumanScore());
                        statement.setString(10, record.getReviewNote());
                        statement.setString(11, record.getReviewStatus());
                        statement.setBoolean(12, record.isEvaluated());
                        statement.setString(13, record.getCreatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save evaluation state");
        }
    }

    public synchronized Optional<DocumentStateData> loadDocumentState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection()) {
            List<DocumentData> documents = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_DOCUMENTS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    documents.add(new DocumentData(
                            resultSet.getString("document_id"),
                            resultSet.getString("document_name"),
                            resultSet.getString("document_title"),
                            resultSet.getLong("document_size"),
                            resultSet.getString("content_type"),
                            resultSet.getString("upload_time"),
                            resultSet.getInt("chunk_count"),
                            resultSet.getBoolean("is_indexed"),
                            resultSet.getString("status_message"),
                            resultSet.getString("storage_path"),
                            resultSet.getString("owner_id"),
                            resultSet.getString("owner_username")
                    ));
                }
            }

            List<DocumentChunkData> chunks = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_DOCUMENT_CHUNKS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunks.add(new DocumentChunkData(
                            resultSet.getString("chunk_id"),
                            resultSet.getString("document_id"),
                            resultSet.getString("chunk_title"),
                            resultSet.getString("chunk_source"),
                            resultSet.getString("chunk_content"),
                            resultSet.getObject("page_number") == null ? null : resultSet.getInt("page_number"),
                            readFloatArray(resultSet.getString("vector_json"))
                    ));
                }
            }

            if (documents.isEmpty() && chunks.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new DocumentStateData(documents, chunks));
        } catch (Exception e) {
            deactivate(e, "load document state");
            return Optional.empty();
        }
    }

    public synchronized void saveDocumentState(DocumentStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_document_chunks");
                executeUpdate(connection, "DELETE FROM platform_documents");

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_documents
                        (document_id, document_name, document_title, document_size, content_type, upload_time, chunk_count,
                         is_indexed, status_message, storage_path, owner_id, owner_username)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (DocumentData document : safeList(state.getDocuments())) {
                        statement.setString(1, document.getId());
                        statement.setString(2, document.getName());
                        statement.setString(3, document.getTitle());
                        statement.setLong(4, document.getSize());
                        statement.setString(5, document.getContentType());
                        statement.setString(6, document.getUploadTime());
                        statement.setInt(7, document.getChunkCount());
                        statement.setBoolean(8, document.isIndexed());
                        statement.setString(9, document.getStatusMessage());
                        statement.setString(10, document.getStoragePath());
                        statement.setString(11, document.getOwnerId());
                        statement.setString(12, document.getOwnerUsername());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_document_chunks
                        (chunk_id, document_id, chunk_title, chunk_source, chunk_content, page_number, vector_json)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (DocumentChunkData chunk : safeList(state.getChunks())) {
                        statement.setString(1, chunk.getId());
                        statement.setString(2, chunk.getDocumentId());
                        statement.setString(3, chunk.getTitle());
                        statement.setString(4, chunk.getSource());
                        statement.setString(5, chunk.getContent());
                        setNullableInteger(statement, 6, chunk.getPage());
                        statement.setString(7, writeJson(chunk.getVector()));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save document state");
        }
    }

    public synchronized Optional<CollaborationStateData> loadCollaborationState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection()) {
            List<StoreProfileData> profiles = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_STORE_PROFILES_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Double rating = resultSet.getObject("rating") == null ? null : resultSet.getDouble("rating");
                    profiles.add(new StoreProfileData(
                            resultSet.getString("shop_id"),
                            resultSet.getString("owner_id"),
                            resultSet.getString("owner_username"),
                            resultSet.getString("shop_name"),
                            resultSet.getString("contact_name"),
                            resultSet.getString("phone"),
                            resultSet.getString("wechat"),
                            resultSet.getString("address"),
                            resultSet.getString("service_area"),
                            resultSet.getString("specialties"),
                            rating,
                            resultSet.getString("created_at"),
                            resultSet.getString("updated_at")
                    ));
                }
            }

            List<RemedyPlanData> plans = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_REMEDY_PLANS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    plans.add(new RemedyPlanData(
                            resultSet.getString("plan_id"),
                            resultSet.getString("shop_id"),
                            resultSet.getString("owner_id"),
                            resultSet.getString("owner_username"),
                            resultSet.getString("shop_name"),
                            resultSet.getString("title"),
                            resultSet.getString("disease_tag"),
                            resultSet.getString("stage_tag"),
                            resultSet.getString("summary"),
                            readStringList(resultSet.getString("products_json")),
                            readStringList(resultSet.getString("usage_tips_json")),
                            readStringList(resultSet.getString("risk_notes_json")),
                            resultSet.getString("inventory_status"),
                            resultSet.getBoolean("is_active"),
                            resultSet.getString("created_at"),
                            resultSet.getString("updated_at"),
                            resultSet.getString("idempotency_key")
                    ));
                }
            }

            List<ConsultationData> consultations = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_CONSULTATIONS_SQL);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    consultations.add(new ConsultationData(
                            resultSet.getString("consultation_id"),
                            resultSet.getString("farmer_user_id"),
                            resultSet.getString("farmer_username"),
                            resultSet.getString("disease_tag"),
                            resultSet.getString("stage_tag"),
                            resultSet.getString("question"),
                            resultSet.getString("plan_id"),
                            resultSet.getString("plan_title"),
                            resultSet.getString("shop_id"),
                            resultSet.getString("shop_name"),
                            resultSet.getString("status_name"),
                            readStringList(resultSet.getString("reason_tags_json")),
                            resultSet.getString("created_at"),
                            resultSet.getString("updated_at")
                    ));
                }
            }

            if (profiles.isEmpty() && plans.isEmpty() && consultations.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CollaborationStateData(profiles, plans, consultations));
        } catch (Exception e) {
            deactivate(e, "load collaboration state");
            return Optional.empty();
        }
    }

    public synchronized void saveCollaborationState(CollaborationStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_consultations");
                executeUpdate(connection, "DELETE FROM platform_remedy_plans");
                executeUpdate(connection, "DELETE FROM platform_store_profiles");

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_store_profiles
                        (shop_id, owner_id, owner_username, shop_name, contact_name, phone, wechat, address,
                         service_area, specialties, rating, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (StoreProfileData profile : safeList(state.getProfiles())) {
                        statement.setString(1, profile.getShopId());
                        statement.setString(2, profile.getOwnerId());
                        statement.setString(3, profile.getOwnerUsername());
                        statement.setString(4, profile.getShopName());
                        statement.setString(5, profile.getContactName());
                        statement.setString(6, profile.getPhone());
                        statement.setString(7, profile.getWechat());
                        statement.setString(8, profile.getAddress());
                        statement.setString(9, profile.getServiceArea());
                        statement.setString(10, profile.getSpecialties());
                        setNullableDouble(statement, 11, profile.getRating());
                        statement.setString(12, profile.getCreatedAt());
                        statement.setString(13, profile.getUpdatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_remedy_plans
                        (plan_id, shop_id, owner_id, owner_username, shop_name, title, disease_tag, stage_tag, summary,
                         products_json, usage_tips_json, risk_notes_json, inventory_status, is_active, created_at, updated_at,
                         idempotency_key)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (RemedyPlanData plan : safeList(state.getPlans())) {
                        statement.setString(1, plan.getId());
                        statement.setString(2, plan.getShopId());
                        statement.setString(3, plan.getOwnerId());
                        statement.setString(4, plan.getOwnerUsername());
                        statement.setString(5, plan.getShopName());
                        statement.setString(6, plan.getTitle());
                        statement.setString(7, plan.getDiseaseTag());
                        statement.setString(8, plan.getStageTag());
                        statement.setString(9, plan.getSummary());
                        statement.setString(10, writeJson(plan.getProducts()));
                        statement.setString(11, writeJson(plan.getUsageTips()));
                        statement.setString(12, writeJson(plan.getRiskNotes()));
                        statement.setString(13, plan.getInventoryStatus());
                        statement.setBoolean(14, plan.isActive());
                        statement.setString(15, plan.getCreatedAt());
                        statement.setString(16, plan.getUpdatedAt());
                        statement.setString(17, plan.getIdempotencyKey());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_consultations
                        (consultation_id, farmer_user_id, farmer_username, disease_tag, stage_tag, question, plan_id,
                         plan_title, shop_id, shop_name, status_name, reason_tags_json, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (ConsultationData consultation : safeList(state.getConsultations())) {
                        statement.setString(1, consultation.getId());
                        statement.setString(2, consultation.getFarmerUserId());
                        statement.setString(3, consultation.getFarmerUsername());
                        statement.setString(4, consultation.getDiseaseTag());
                        statement.setString(5, consultation.getStageTag());
                        statement.setString(6, consultation.getQuestion());
                        statement.setString(7, consultation.getPlanId());
                        statement.setString(8, consultation.getPlanTitle());
                        statement.setString(9, consultation.getShopId());
                        statement.setString(10, consultation.getShopName());
                        statement.setString(11, consultation.getStatus());
                        statement.setString(12, writeJson(consultation.getReasonTags()));
                        statement.setString(13, consultation.getCreatedAt());
                        statement.setString(14, consultation.getUpdatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save collaboration state");
        }
    }

    public synchronized Optional<FeedbackStateData> loadFeedbackState() {
        if (!active) {
            return Optional.empty();
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_FEEDBACK_RECORDS_SQL);
             ResultSet resultSet = statement.executeQuery()) {
            List<FeedbackRecordData> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(new FeedbackRecordData(
                        resultSet.getString("feedback_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("role_name"),
                        resultSet.getString("module_name"),
                        resultSet.getInt("overall_score"),
                        resultSet.getInt("accuracy_score"),
                        resultSet.getInt("practicality_score"),
                        resultSet.getInt("fluency_score"),
                        resultSet.getString("comment"),
                        resultSet.getString("created_at")
                ));
            }
            return records.isEmpty() ? Optional.empty() : Optional.of(new FeedbackStateData(records));
        } catch (Exception e) {
            deactivate(e, "load feedback state");
            return Optional.empty();
        }
    }

    public synchronized void saveFeedbackState(FeedbackStateData state) {
        if (!active || state == null) {
            return;
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                executeUpdate(connection, "DELETE FROM platform_feedback_records");
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO platform_feedback_records
                        (feedback_id, user_id, username, role_name, module_name, overall_score, accuracy_score,
                         practicality_score, fluency_score, comment, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (FeedbackRecordData record : safeList(state.getRecords())) {
                        statement.setString(1, record.getId());
                        statement.setString(2, record.getUserId());
                        statement.setString(3, record.getUsername());
                        statement.setString(4, record.getRole());
                        statement.setString(5, record.getModule());
                        statement.setInt(6, record.getOverallScore());
                        statement.setInt(7, record.getAccuracyScore());
                        statement.setInt(8, record.getPracticalityScore());
                        statement.setInt(9, record.getFluencyScore());
                        statement.setString(10, record.getComment());
                        statement.setString(11, record.getCreatedAt());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                rollbackQuietly(connection);
                throw e;
            } finally {
                resetAutoCommit(connection);
            }
        } catch (Exception e) {
            deactivate(e, "save feedback state");
        }
    }

    private void ensureSchema() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_USERS_SQL);
            statement.execute(CREATE_SESSIONS_SQL);
            statement.execute(CREATE_CHAT_MESSAGES_SQL);
            statement.execute(CREATE_EVALUATIONS_SQL);
            executeAlterAddColumn(statement, ALTER_EVALUATIONS_ADD_AUTO_SCORE_SQL);
            executeAlterAddColumn(statement, ALTER_EVALUATIONS_ADD_SCORE_BREAKDOWN_SQL);
            executeAlterAddColumn(statement, ALTER_EVALUATIONS_ADD_REVIEW_NOTE_SQL);
            executeAlterAddColumn(statement, ALTER_EVALUATIONS_ADD_REVIEW_STATUS_SQL);
            statement.execute(CREATE_DOCUMENTS_SQL);
            statement.execute(CREATE_DOCUMENT_CHUNKS_SQL);
            statement.execute(CREATE_STORE_PROFILES_SQL);
            statement.execute(CREATE_REMEDY_PLANS_SQL);
            statement.execute(CREATE_CONSULTATIONS_SQL);
            statement.execute(CREATE_FEEDBACK_RECORDS_SQL);
            statement.execute(CREATE_AGENT_RUNS_SQL);
            statement.execute(CREATE_AGENT_STEPS_SQL);
            statement.execute(CREATE_AGENT_APPROVALS_SQL);
            statement.execute(CREATE_TENANTS_SQL);
            statement.execute("INSERT IGNORE INTO platform_tenants (tenant_id, tenant_name, tenant_type, status_name, created_at, updated_at) VALUES ('tenant-default', '默认演示租户', 'cooperative', 'active', NOW(), NOW())");
            statement.execute(CREATE_ORCHARDS_SQL);
            statement.execute(CREATE_OUTBOX_EVENTS_SQL);
            for (String migration : TENANT_COLUMN_MIGRATIONS) {
                executeAlterAddColumn(statement, migration);
            }
            for (String migration : AGENT_COLUMN_MIGRATIONS) {
                executeAlterAddColumn(statement, migration);
            }
            for (String migration : IDEMPOTENCY_COLUMN_MIGRATIONS) {
                executeAlterAddColumn(statement, migration);
            }
        }
    }

    private void executeAlterAddColumn(Statement statement, String sql) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate column name")) {
                return;
            }
            throw e;
        }
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(url, username, password);
    }

    private void executeUpdate(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (Exception ignored) {
            // Ignore rollback failure, the original exception is more important.
        }
    }

    private void resetAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (Exception ignored) {
            // Ignore cleanup failure.
        }
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
            return;
        }
        statement.setDouble(index, value);
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }
        statement.setInt(index, value);
    }

    private List<ChatResponse.Source> readSources(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SOURCES_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read chat sources JSON", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read map JSON", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read string list JSON", e);
        }
    }

    private float[] readFloatArray(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new float[0];
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read vector JSON", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write JSON payload", e);
        }
    }

    private EvaluationRubricScore readRubricScore(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, EvaluationRubricScore.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read evaluation rubric JSON", e);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void deactivate(Exception exception, String action) {
        active = false;
        log.warn("MySQL structured storage failed to {}, switching back to local state files", action, exception);
    }

    private String sanitizeUrl(String jdbcUrl) {
        int index = jdbcUrl.indexOf('?');
        return index >= 0 ? jdbcUrl.substring(0, index) : jdbcUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthStateData {
        private List<AuthUserData> users = new ArrayList<>();
        private List<AuthSessionData> sessions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthUserData {
        private String id;
        private String username;
        private String passwordHash;
        private String passwordSalt;
        private String role;
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthSessionData {
        private String token;
        private String userId;
        private String createdAt;
        private String expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatHistoryStateData {
        private List<ChatMessageData> records = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageData {
        private String id;
        private String userId;
        private String sessionId;
        private String question;
        private String answer;
        private List<ChatResponse.Source> sources = new ArrayList<>();
        private Map<String, Object> knowledgeGraph = Map.of();
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationStateData {
        private List<EvaluationRecordData> records = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationRecordData {
        private long id;
        private String type;
        private String question;
        private String referenceAnswer;
        private String systemAnswer;
        private Double autoScore;
        private EvaluationRubricScore scoreBreakdown;
        private Double bleuScore;
        private Integer humanScore;
        private String reviewNote;
        private String reviewStatus;
        private boolean evaluated;
        private String createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStateData {
        private List<DocumentData> documents = new ArrayList<>();
        private List<DocumentChunkData> chunks = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentData {
        private String id;
        private String name;
        private String title;
        private long size;
        private String contentType;
        private String uploadTime;
        private int chunkCount;
        private boolean indexed;
        private String statusMessage;
        private String storagePath;
        private String ownerId;
        private String ownerUsername;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentChunkData {
        private String id;
        private String documentId;
        private String title;
        private String source;
        private String content;
        private Integer page;
        private float[] vector;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollaborationStateData {
        private List<StoreProfileData> profiles = new ArrayList<>();
        private List<RemedyPlanData> plans = new ArrayList<>();
        private List<ConsultationData> consultations = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreProfileData {
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemedyPlanData {
        private String id;
        private String shopId;
        private String ownerId;
        private String ownerUsername;
        private String shopName;
        private String title;
        private String diseaseTag;
        private String stageTag;
        private String summary;
        private List<String> products = new ArrayList<>();
        private List<String> usageTips = new ArrayList<>();
        private List<String> riskNotes = new ArrayList<>();
        private String inventoryStatus;
        private boolean active;
        private String createdAt;
        private String updatedAt;
        private String idempotencyKey;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsultationData {
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
        private List<String> reasonTags = new ArrayList<>();
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackStateData {
        private List<FeedbackRecordData> records = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRecordData {
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrchardData {
        private String id;
        private String tenantId;
        private String ownerId;
        private String name;
        private String location;
        private String variety;
        private String growthStage;
        private BigDecimal areaMu;
        private String createdAt;
        private String updatedAt;
    }
}
