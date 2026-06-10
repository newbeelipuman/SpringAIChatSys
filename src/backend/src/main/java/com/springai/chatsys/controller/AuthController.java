package com.springai.chatsys.controller;

import com.springai.chatsys.auth.CurrentUser;
import com.springai.chatsys.auth.AuthAccessService;
import com.springai.chatsys.auth.AuthPermission;
import com.springai.chatsys.auth.AuthRole;
import com.springai.chatsys.auth.IdentityResolver;
import com.springai.chatsys.dto.AdminPasswordResetRequest;
import com.springai.chatsys.dto.AdminRoleUpdateRequest;
import com.springai.chatsys.dto.AdminUserDTO;
import com.springai.chatsys.dto.AuthChangePasswordRequest;
import com.springai.chatsys.dto.AuthHealthResponse;
import com.springai.chatsys.dto.AuthLoginRequest;
import com.springai.chatsys.dto.AuthRegisterRequest;
import com.springai.chatsys.dto.AuthResponse;
import com.springai.chatsys.dto.AuthUserDTO;
import com.springai.chatsys.mysql.service.MysqlAuthService;
import com.springai.chatsys.mysql.service.MysqlDiagnosticsService;
import com.springai.chatsys.mysql.service.MysqlPermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IdentityResolver identityResolver;
    private final AuthAccessService authAccessService;
    private final Optional<MysqlAuthService> mysqlAuthService;
    private final Optional<MysqlDiagnosticsService> mysqlDiagnosticsService;
    private final Optional<MysqlPermissionService> mysqlPermissionService;

    public AuthController(
            IdentityResolver identityResolver,
            AuthAccessService authAccessService,
            Optional<MysqlAuthService> mysqlAuthService,
            Optional<MysqlDiagnosticsService> mysqlDiagnosticsService,
            Optional<MysqlPermissionService> mysqlPermissionService
    ) {
        this.identityResolver = identityResolver;
        this.authAccessService = authAccessService;
        this.mysqlAuthService = mysqlAuthService;
        this.mysqlDiagnosticsService = mysqlDiagnosticsService;
        this.mysqlPermissionService = mysqlPermissionService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody AuthRegisterRequest request) {
        return requireAuthService().register(request.username(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthLoginRequest request) {
        return requireAuthService().login(request.username(), request.password());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Optional<String> token = identityResolver.tokenFrom(authorization);
        boolean revoked = token.filter(value -> mysqlAuthService.map(service -> service.logout(value)).orElse(false)).isPresent();
        return Map.of("revoked", revoked);
    }

    @PostMapping("/password/change")
    public Map<String, Object> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AuthChangePasswordRequest request
    ) {
        String token = identityResolver.tokenFrom(authorization)
                .orElseThrow(() -> new IllegalArgumentException("Login required."));
        return requireAuthService().changePassword(token, request.currentPassword(), request.newPassword());
    }

    @GetMapping("/me")
    public AuthUserDTO me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser
    ) {
        return toDto(identityResolver.resolve(authorization, demoUser));
    }

    @GetMapping("/health")
    public AuthHealthResponse health(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser
    ) {
        CurrentUser currentUser = identityResolver.resolve(authorization, demoUser);
        return new AuthHealthResponse(
                mysqlAuthService.isPresent(),
                mysqlAuthService.isPresent() ? "mysql-demo-token" : "demo-fallback-only",
                currentUser.source(),
                currentUser.role(),
                mysqlDiagnosticsService.map(service -> service.tableReady("app_user")).orElse(false),
                mysqlDiagnosticsService.map(service -> service.tableReady("auth_session")).orElse(false),
                mysqlDiagnosticsService.map(service -> service.tableReady("knowledge_permission")).orElse(false),
                mysqlAuthService.map(service -> service.adminUserCount() > 0).orElse(false),
                true,
                lastError()
        );
    }

    @GetMapping("/admin/permissions")
    public Map<String, List<String>> adminPermissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser
    ) {
        CurrentUser currentUser = identityResolver.resolve(authorization, demoUser);
        authAccessService.requirePermission(currentUser, AuthPermission.MANAGE_USERS);
        return Arrays.stream(AuthRole.values())
                .collect(Collectors.toMap(
                        AuthRole::name,
                        role -> role.permissions().stream().map(AuthPermission::name).sorted().toList()
                ));
    }

    @GetMapping("/admin/users")
    public List<AdminUserDTO> adminUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser
    ) {
        CurrentUser currentUser = identityResolver.resolve(authorization, demoUser);
        authAccessService.requirePermission(currentUser, AuthPermission.MANAGE_USERS);
        return requireAuthService().users();
    }

    @PostMapping("/admin/users/{username}/role")
    public AdminUserDTO updateUserRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser,
            @PathVariable String username,
            @Valid @RequestBody AdminRoleUpdateRequest request
    ) {
        CurrentUser currentUser = identityResolver.resolve(authorization, demoUser);
        authAccessService.requirePermission(currentUser, AuthPermission.ASSIGN_ROLES);
        return requireAuthService().updateRole(username, request.role(), currentUser.userKey());
    }

    @PostMapping("/admin/users/{username}/password/reset")
    public Map<String, Object> resetUserPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Demo-User", required = false) String demoUser,
            @PathVariable String username,
            @Valid @RequestBody AdminPasswordResetRequest request
    ) {
        CurrentUser currentUser = identityResolver.resolve(authorization, demoUser);
        authAccessService.requirePermission(currentUser, AuthPermission.RESET_USER_PASSWORD);
        return requireAuthService().resetPassword(username, request.newPassword(), currentUser.userKey());
    }

    private MysqlAuthService requireAuthService() {
        return mysqlAuthService.orElseThrow(() -> new IllegalStateException("Auth requires MYSQL_ENABLED=true and MySQL auth tables."));
    }

    private String lastError() {
        return mysqlAuthService.map(MysqlAuthService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .or(() -> mysqlPermissionService.map(MysqlPermissionService::lastError).filter(error -> error != null && !error.isBlank()))
                .orElse("");
    }

    private static AuthUserDTO toDto(CurrentUser user) {
        return new AuthUserDTO(user.userKey(), user.username(), user.displayName(), user.role(), user.source(), user.authenticated());
    }
}
