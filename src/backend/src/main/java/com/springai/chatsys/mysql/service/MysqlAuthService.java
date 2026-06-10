package com.springai.chatsys.mysql.service;

import com.springai.chatsys.auth.CurrentUser;
import com.springai.chatsys.auth.AuthRole;
import com.springai.chatsys.dto.AdminUserDTO;
import com.springai.chatsys.dto.AuthResponse;
import com.springai.chatsys.dto.AuthUserDTO;
import com.springai.chatsys.mysql.entity.AppUserEntity;
import com.springai.chatsys.mysql.entity.AuthSessionEntity;
import com.springai.chatsys.mysql.repository.AppUserRepository;
import com.springai.chatsys.mysql.repository.AuthSessionRepository;
import com.springai.chatsys.service.DemoUserContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mysql", name = "enabled", havingValue = "true")
public class MysqlAuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String AUTH_PROVIDER = "local-demo";
    private static final String DEFAULT_ROLE = AuthRole.USER.name();

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private volatile String lastError;

    public MysqlAuthService(
            AppUserRepository appUserRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Username already exists.");
        }
        AppUserEntity user = new AppUserEntity();
        user.setUsername(normalizedUsername);
        user.setUserKey(DemoUserContext.resolve("auth-" + normalizedUsername));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setAuthProvider(AUTH_PROVIDER);
        user.setRole(DEFAULT_ROLE);
        user.setEnabled(true);
        AppUserEntity saved = appUserRepository.save(user);
        lastError = null;
        return issueToken(saved);
    }

    @Transactional
    public boolean bootstrapAdmin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        try {
            if (appUserRepository.existsByRole(AuthRole.ADMIN.name())) {
                lastError = null;
                return false;
            }
            String normalizedUsername = normalizeUsername(username);
            AppUserEntity user = appUserRepository.findByUsername(normalizedUsername).orElseGet(AppUserEntity::new);
            if (user.getUsername() == null) {
                user.setUsername(normalizedUsername);
                user.setUserKey(DemoUserContext.resolve("auth-" + normalizedUsername));
                user.setAuthProvider(AUTH_PROVIDER);
                user.setEnabled(true);
            }
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRole(AuthRole.ADMIN.name());
            user.setEnabled(true);
            appUserRepository.save(user);
            lastError = null;
            return true;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return false;
        }
    }

    @Transactional
    public AuthResponse login(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        AppUserEntity user = appUserRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("User is disabled.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password.");
        }
        lastError = null;
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public Optional<CurrentUser> currentUser(String token) {
        try {
            String tokenHash = hashToken(token);
            return authSessionRepository.findByTokenHash(tokenHash)
                    .filter(session -> !session.isRevoked())
                    .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                    .map(session -> CurrentUser.authenticated(session.getUserKey(), session.getUsername(), session.getRole()));
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return Optional.empty();
        }
    }

    @Transactional
    public boolean logout(String token) {
        try {
            Optional<AuthSessionEntity> session = authSessionRepository.findByTokenHash(hashToken(token));
            session.ifPresent(value -> {
                value.setRevoked(true);
                authSessionRepository.save(value);
            });
            lastError = null;
            return session.isPresent();
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return false;
        }
    }

    @Transactional
    public Map<String, Object> changePassword(String token, String currentPassword, String newPassword) {
        try {
            AuthSessionEntity session = authSessionRepository.findByTokenHash(hashToken(token))
                    .filter(value -> !value.isRevoked())
                    .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                    .orElseThrow(() -> new IllegalArgumentException("Login required."));
            AppUserEntity user = appUserRepository.findByUsername(session.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("Login user no longer exists."));
            if (!user.isEnabled()) {
                throw new IllegalArgumentException("User is disabled.");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("New password must be different from the current password.");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            appUserRepository.save(user);

            List<AuthSessionEntity> activeSessions = authSessionRepository.findByUserKeyAndRevokedFalse(user.getUserKey());
            activeSessions.forEach(value -> value.setRevoked(true));
            authSessionRepository.saveAll(activeSessions);
            lastError = null;
            return Map.of("changed", true, "revokedSessions", activeSessions.size());
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AdminUserDTO> users() {
        try {
            List<AdminUserDTO> users = appUserRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toAdminUserDto)
                    .toList();
            lastError = null;
            return users;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional
    public AdminUserDTO updateRole(String username, String role, String operatorUserKey) {
        try {
            String normalizedUsername = normalizeUsername(username);
            AuthRole normalizedRole = AuthRole.require(role);
            AppUserEntity user = appUserRepository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist."));
            if (user.getUserKey().equals(operatorUserKey)) {
                throw new IllegalArgumentException("Administrators cannot change their own role from this endpoint.");
            }
            if (AuthRole.from(user.getRole()) == AuthRole.ADMIN
                    && normalizedRole != AuthRole.ADMIN
                    && adminUserCount() <= 1) {
                throw new IllegalArgumentException("At least one ADMIN user must remain.");
            }
            user.setRole(normalizedRole.name());
            AppUserEntity saved = appUserRepository.save(user);
            revokeOtherUserSessions(saved.getUserKey(), operatorUserKey);
            lastError = null;
            return toAdminUserDto(saved);
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> resetPassword(String username, String newPassword, String operatorUserKey) {
        try {
            String normalizedUsername = normalizeUsername(username);
            AppUserEntity user = appUserRepository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist."));
            if (user.getUserKey().equals(operatorUserKey)) {
                throw new IllegalArgumentException("Use the personal password change endpoint for the current account.");
            }
            if (!user.isEnabled()) {
                throw new IllegalArgumentException("User is disabled.");
            }
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw new IllegalArgumentException("New password must be different from the current password.");
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            appUserRepository.save(user);
            int revokedSessions = revokeOtherUserSessions(user.getUserKey(), operatorUserKey);
            lastError = null;
            return Map.of("reset", true, "revokedSessions", revokedSessions);
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Optional<AdminUserDTO> userByUserKey(String userKey) {
        try {
            Optional<AdminUserDTO> user = appUserRepository.findByUserKey(DemoUserContext.resolve(userKey))
                    .map(this::toAdminUserDto);
            lastError = null;
            return user;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public long totalUsers() {
        try {
            long count = appUserRepository.count();
            lastError = null;
            return count;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public long totalSessions() {
        try {
            long count = authSessionRepository.count();
            lastError = null;
            return count;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public long adminUserCount() {
        try {
            long count = appUserRepository.countByRole(AuthRole.ADMIN.name());
            lastError = null;
            return count;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Long> userRoleCount() {
        try {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Object[] row : appUserRepository.countUsersByRole()) {
                String role = row[0] == null ? AuthRole.USER.name() : AuthRole.from(row[0].toString()).name();
                Long count = row[1] instanceof Number number ? number.longValue() : 0L;
                counts.put(role, count);
            }
            for (AuthRole role : AuthRole.values()) {
                counts.putIfAbsent(role.name(), 0L);
            }
            lastError = null;
            return counts;
        } catch (RuntimeException ex) {
            recordFailure(ex);
            return Map.of();
        }
    }

    public String lastError() {
        return lastError;
    }

    public void recordFailure(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            lastError = ex.getClass().getSimpleName();
            return;
        }
        lastError = message.length() > 240 ? message.substring(0, 240) + "..." : message;
    }

    private AuthResponse issueToken(AppUserEntity user) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        AuthSessionEntity session = new AuthSessionEntity();
        session.setTokenHash(hashToken(token));
        session.setUserKey(user.getUserKey());
        session.setUsername(user.getUsername());
        session.setRole(user.getRole());
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().plus(12, ChronoUnit.HOURS));
        authSessionRepository.save(session);
        return new AuthResponse(token, TOKEN_TYPE, toDto(CurrentUser.authenticated(user.getUserKey(), user.getUsername(), user.getRole())));
    }

    private int revokeOtherUserSessions(String userKey, String operatorUserKey) {
        List<AuthSessionEntity> activeSessions = authSessionRepository.findByUserKeyAndRevokedFalse(userKey);
        activeSessions.stream()
                .filter(session -> operatorUserKey == null || !operatorUserKey.equals(session.getUserKey()))
                .forEach(session -> session.setRevoked(true));
        authSessionRepository.saveAll(activeSessions);
        return (int) activeSessions.stream()
                .filter(AuthSessionEntity::isRevoked)
                .count();
    }

    private AdminUserDTO toAdminUserDto(AppUserEntity user) {
        return new AdminUserDTO(
                user.getUserKey(),
                user.getUsername(),
                AuthRole.from(user.getRole()).name(),
                user.isEnabled(),
                user.getCreatedAt() == null ? "" : user.getCreatedAt().toString(),
                user.getUpdatedAt() == null ? "" : user.getUpdatedAt().toString()
        );
    }

    private static AuthUserDTO toDto(CurrentUser user) {
        return new AuthUserDTO(user.userKey(), user.username(), user.displayName(), user.role(), user.source(), user.authenticated());
    }

    private static String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim().toLowerCase();
        if (!value.matches("[a-z0-9_.@-]{3,64}")) {
            throw new IllegalArgumentException("Username must be 3-64 characters and only contain letters, numbers, dot, underscore, at sign or dash.");
        }
        return value;
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
