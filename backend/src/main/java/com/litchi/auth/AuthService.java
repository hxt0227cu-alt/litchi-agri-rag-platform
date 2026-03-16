package com.litchi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.litchi.dto.AuthResponse;
import com.litchi.dto.AuthUserView;
import com.litchi.dto.LoginRequest;
import com.litchi.dto.RegisterRequest;
import com.litchi.service.MysqlStateStoreService;
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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int HASH_ITERATIONS = 65_536;
    private static final int HASH_BYTES = 32;
    private static final String SNAPSHOT_KEY = "auth";

    private final ObjectMapper objectMapper;
    private final MysqlStateStoreService mysqlStateStoreService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.state-file:data/auth-state.json}")
    private String stateFile;

    @Value("${app.auth.session-days:7}")
    private long sessionDays;

    private final Map<String, UserRecord> usersById = new LinkedHashMap<>();
    private final Map<String, SessionRecord> sessionsByToken = new LinkedHashMap<>();

    private Path statePath;

    @PostConstruct
    public void init() {
        statePath = resolvePath(stateFile);
        try {
            if (statePath.getParent() != null) {
                Files.createDirectories(statePath.getParent());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare auth state directory", e);
        }

        loadState();
        if (usersById.isEmpty()) {
            seedDefaultUsers();
        } else {
            pruneExpiredSessions();
        }
    }

    public synchronized AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();
        String role = normalizeRole(request.getRole());

        validateCredentials(username, password);
        ensureUsernameAvailable(username);

        UserRecord user = newUser(username, password, role);
        usersById.put(user.getId(), user);
        persistState();
        return createSession(user);
    }

    public synchronized AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword().trim();

        UserRecord user = usersById.values().stream()
                .filter(item -> item.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误。"));

        if (!verifyPassword(password, user.getPasswordSalt(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误。");
        }

        return createSession(user);
    }

    public synchronized void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionsByToken.remove(token);
        persistState();
    }

    public synchronized AuthenticatedUser resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        SessionRecord session = sessionsByToken.get(token);
        if (session == null) {
            return null;
        }

        if (isExpired(session.getExpiresAt())) {
            sessionsByToken.remove(token);
            persistState();
            return null;
        }

        UserRecord user = usersById.get(session.getUserId());
        if (user == null) {
            sessionsByToken.remove(token);
            persistState();
            return null;
        }

        return toAuthenticatedUser(user);
    }

    public synchronized AuthUserView me(String token) {
        AuthenticatedUser user = resolveUser(token);
        if (user == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录。");
        }
        return toView(user);
    }

    private AuthResponse createSession(UserRecord user) {
        pruneExpiredSessions();

        String token = UUID.randomUUID().toString().replace("-", "");
        String expiresAt = OffsetDateTime.now().plus(sessionDays, ChronoUnit.DAYS).toString();
        SessionRecord session = SessionRecord.builder()
                .token(token)
                .userId(user.getId())
                .createdAt(OffsetDateTime.now().toString())
                .expiresAt(expiresAt)
                .build();
        sessionsByToken.put(token, session);
        persistState();

        return AuthResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .user(toView(toAuthenticatedUser(user)))
                .build();
    }

    private void validateCredentials(String username, String password) {
        if (username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度需在 3 到 32 个字符之间。");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位。");
        }
    }

    private void ensureUsernameAvailable(String username) {
        boolean exists = usersById.values().stream()
                .anyMatch(item -> item.getUsername().equalsIgnoreCase(username));
        if (exists) {
            throw new IllegalArgumentException("用户名已存在。");
        }
    }

    private UserRecord newUser(String username, String password, String role) {
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hashedPassword = hashPassword(password, salt);

        return UserRecord.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .username(username)
                .passwordSalt(salt)
                .passwordHash(hashedPassword)
                .role(role)
                .createdAt(OffsetDateTime.now().toString())
                .build();
    }

    private String normalizeUsername(String username) {
        return Optional.ofNullable(username)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("用户名不能为空。"));
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "farmer" : role.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "farmer", "technician", "shopkeeper" -> normalized;
            default -> throw new IllegalArgumentException("角色必须是 farmer、technician 或 shopkeeper。");
        };
    }

    private boolean verifyPassword(String password, String salt, String expectedHash) {
        return hashPassword(password, salt).equals(expectedHash);
    }

    private String hashPassword(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    Base64.getDecoder().decode(salt),
                    HASH_ITERATIONS,
                    HASH_BYTES * 8
            );
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash password", e);
        }
    }

    private void seedDefaultUsers() {
        List<UserRecord> defaults = List.of(
                newUser("farmer", "demo123", "farmer"),
                newUser("technician", "demo123", "technician"),
                newUser("shopkeeper", "demo123", "shopkeeper")
        );
        defaults.forEach(user -> usersById.put(user.getId(), user));
        persistState();
        log.info("Seeded {} default users for the full platform", defaults.size());
    }

    private void pruneExpiredSessions() {
        List<String> expiredTokens = sessionsByToken.values().stream()
                .filter(session -> isExpired(session.getExpiresAt()))
                .map(SessionRecord::getToken)
                .toList();
        expiredTokens.forEach(sessionsByToken::remove);
    }

    private boolean isExpired(String value) {
        try {
            return OffsetDateTime.parse(value).isBefore(OffsetDateTime.now());
        } catch (Exception e) {
            return true;
        }
    }

    private AuthenticatedUser toAuthenticatedUser(UserRecord user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }

    private AuthUserView toView(AuthenticatedUser user) {
        return AuthUserView.builder()
                .id(user.id())
                .username(user.username())
                .role(user.role())
                .createdAt(user.createdAt())
                .build();
    }

    private void loadState() {
        if (Files.exists(statePath)) {
            try {
                applyState(objectMapper.readValue(statePath.toFile(), AuthState.class));
            } catch (IOException e) {
                log.warn("Failed to load auth state, starting from an empty state", e);
                usersById.clear();
                sessionsByToken.clear();
            }
        }

        if (!mysqlStateStoreService.isActive()) {
            return;
        }

        Optional<MysqlStateStoreService.AuthStateData> mysqlState = mysqlStateStoreService.loadAuthState();
        if (mysqlState.isPresent()) {
            applyState(fromMysqlState(mysqlState.get()));
            persistLocalState();
            return;
        }

        if (!usersById.isEmpty() || !sessionsByToken.isEmpty()) {
            mysqlStateStoreService.saveAuthState(toMysqlState(
                    new AuthState(new ArrayList<>(usersById.values()), new ArrayList<>(sessionsByToken.values()))
            ));
        }
    }

    private void persistState() {
        AuthState state = new AuthState(new ArrayList<>(usersById.values()), new ArrayList<>(sessionsByToken.values()));
        persistLocalState(state);
        mysqlStateStoreService.saveAuthState(toMysqlState(state));
    }

    private void persistLocalState() {
        persistLocalState(new AuthState(new ArrayList<>(usersById.values()), new ArrayList<>(sessionsByToken.values())));
    }

    private void persistLocalState(AuthState state) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(statePath.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist auth state", e);
        }
    }

    private void applyState(AuthState state) {
        usersById.clear();
        sessionsByToken.clear();
        if (state == null) {
            return;
        }
        if (state.getUsers() != null) {
            state.getUsers().forEach(user -> usersById.put(user.getId(), user));
        }
        if (state.getSessions() != null) {
            state.getSessions().forEach(session -> sessionsByToken.put(session.getToken(), session));
        }
    }

    private MysqlStateStoreService.AuthStateData toMysqlState(AuthState state) {
        List<MysqlStateStoreService.AuthUserData> users = state.getUsers() == null
                ? List.of()
                : state.getUsers().stream()
                .map(user -> new MysqlStateStoreService.AuthUserData(
                        user.getId(),
                        user.getUsername(),
                        user.getPasswordHash(),
                        user.getPasswordSalt(),
                        user.getRole(),
                        user.getCreatedAt()
                ))
                .toList();

        List<MysqlStateStoreService.AuthSessionData> sessions = state.getSessions() == null
                ? List.of()
                : state.getSessions().stream()
                .map(session -> new MysqlStateStoreService.AuthSessionData(
                        session.getToken(),
                        session.getUserId(),
                        session.getCreatedAt(),
                        session.getExpiresAt()
                ))
                .toList();

        return new MysqlStateStoreService.AuthStateData(users, sessions);
    }

    private AuthState fromMysqlState(MysqlStateStoreService.AuthStateData state) {
        List<UserRecord> users = state.getUsers() == null
                ? List.of()
                : state.getUsers().stream()
                .map(user -> UserRecord.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .passwordHash(user.getPasswordHash())
                        .passwordSalt(user.getPasswordSalt())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
                .toList();

        List<SessionRecord> sessions = state.getSessions() == null
                ? List.of()
                : state.getSessions().stream()
                .map(session -> SessionRecord.builder()
                        .token(session.getToken())
                        .userId(session.getUserId())
                        .createdAt(session.getCreatedAt())
                        .expiresAt(session.getExpiresAt())
                        .build())
                .toList();

        return new AuthState(new ArrayList<>(users), new ArrayList<>(sessions));
    }

    private Path resolvePath(String configuredPath) {
        Path path = Paths.get(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path applicationDir = new ApplicationHome(AuthService.class).getDir().toPath().toAbsolutePath();
        return applicationDir.resolve(path).normalize();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class UserRecord {
        private String id;
        private String username;
        private String passwordHash;
        private String passwordSalt;
        private String role;
        private String createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SessionRecord {
        private String token;
        private String userId;
        private String createdAt;
        private String expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AuthState {
        private List<UserRecord> users = new ArrayList<>();
        private List<SessionRecord> sessions = new ArrayList<>();
    }
}
