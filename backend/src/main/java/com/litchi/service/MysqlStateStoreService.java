package com.litchi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.ChatResponse;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                INDEX idx_platform_sessions_user_id (user_id)
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
                bleu_score DOUBLE NULL,
                human_score INT NULL,
                evaluated BOOLEAN NOT NULL,
                created_at VARCHAR(64) NOT NULL,
                INDEX idx_platform_evaluations_type (question_type),
                INDEX idx_platform_evaluations_evaluated (evaluated)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;

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
            SELECT evaluation_id, question_type, question, reference_answer, system_answer, bleu_score, human_score, evaluated, created_at
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

    private static final TypeReference<List<ChatResponse.Source>> SOURCES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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
        }
    }

    public boolean isActive() {
        return active;
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
                Double bleuScore = resultSet.getObject("bleu_score") == null ? null : resultSet.getDouble("bleu_score");
                Integer humanScore = resultSet.getObject("human_score") == null ? null : resultSet.getInt("human_score");
                records.add(new EvaluationRecordData(
                        resultSet.getLong("evaluation_id"),
                        resultSet.getString("question_type"),
                        resultSet.getString("question"),
                        resultSet.getString("reference_answer"),
                        resultSet.getString("system_answer"),
                        bleuScore,
                        humanScore,
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
                        (evaluation_id, question_type, question, reference_answer, system_answer, bleu_score, human_score, evaluated, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (EvaluationRecordData record : safeList(state.getRecords())) {
                        statement.setLong(1, record.getId());
                        statement.setString(2, record.getType());
                        statement.setString(3, record.getQuestion());
                        statement.setString(4, record.getReferenceAnswer());
                        statement.setString(5, record.getSystemAnswer());
                        setNullableDouble(statement, 6, record.getBleuScore());
                        setNullableInteger(statement, 7, record.getHumanScore());
                        statement.setBoolean(8, record.isEvaluated());
                        statement.setString(9, record.getCreatedAt());
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

    private void ensureSchema() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_USERS_SQL);
            statement.execute(CREATE_SESSIONS_SQL);
            statement.execute(CREATE_CHAT_MESSAGES_SQL);
            statement.execute(CREATE_EVALUATIONS_SQL);
            statement.execute(CREATE_DOCUMENTS_SQL);
            statement.execute(CREATE_DOCUMENT_CHUNKS_SQL);
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
        private Double bleuScore;
        private Integer humanScore;
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
}
