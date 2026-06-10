package com.springai.chatsys.mysql.service;

import com.springai.chatsys.mysql.config.MysqlProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@EnableConfigurationProperties(MysqlProperties.class)
public class MysqlStatusService {

    private final MysqlProperties properties;
    private final Optional<DataSource> dataSource;
    private final Optional<MysqlDemoUserService> demoUserService;
    private final Optional<MysqlBusinessSummaryService> businessSummaryService;
    private final Optional<MysqlDiagnosticsService> diagnosticsService;
    private final Optional<MysqlHistoryMirrorService> historyMirrorService;
    private final Optional<MysqlSuggestionCacheService> suggestionCacheService;
    private final Optional<MysqlAuthService> authService;
    private final Optional<MysqlPermissionService> permissionService;

    public MysqlStatusService(
            MysqlProperties properties,
            Optional<DataSource> dataSource,
            Optional<MysqlDemoUserService> demoUserService,
            Optional<MysqlBusinessSummaryService> businessSummaryService,
            Optional<MysqlDiagnosticsService> diagnosticsService,
            Optional<MysqlHistoryMirrorService> historyMirrorService,
            Optional<MysqlSuggestionCacheService> suggestionCacheService,
            Optional<MysqlAuthService> authService,
            Optional<MysqlPermissionService> permissionService
    ) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.demoUserService = demoUserService;
        this.businessSummaryService = businessSummaryService;
        this.diagnosticsService = diagnosticsService;
        this.historyMirrorService = historyMirrorService;
        this.suggestionCacheService = suggestionCacheService;
        this.authService = authService;
        this.permissionService = permissionService;
    }

    public Map<String, Object> statusDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("mysqlEnabled", properties.isEnabled());
        details.put("mysqlAvailable", false);
        details.put("mysqlHistoryMirrorEnabled", properties.isEnabled() && historyMirrorService.isPresent());
        details.put("mysqlHistoryWriteEnabled", properties.isEnabled() && historyMirrorService.isPresent());
        details.put("mysqlHistoryReadEnabled", properties.isEnabled() && properties.isHistoryReadEnabled() && historyMirrorService.isPresent());
        details.put("mysqlDemoUserUpsertEnabled", properties.isEnabled() && demoUserService.isPresent());
        details.put("mysqlBusinessSummaryEnabled", properties.isEnabled() && businessSummaryService.isPresent());
        details.put("mysqlDiagnosticsEnabled", properties.isEnabled() && diagnosticsService.isPresent());
        details.put("mysqlSuggestionCacheEnabled", properties.isEnabled() && suggestionCacheService.isPresent());
        details.put("authEnabled", properties.isEnabled() && authService.isPresent());
        details.put("authMode", authService.isPresent() ? "mysql-demo-token" : "demo-fallback-only");
        details.put("adminUserAvailable", authService.map(service -> service.adminUserCount() > 0).orElse(false));
        details.put("adminEndpointsProtected", true);
        details.put("permissionRelationAvailable", properties.isEnabled() && diagnosticsService.map(service -> service.tableReady("knowledge_permission")).orElse(false));
        details.put("mysqlUserTableAvailable", properties.isEnabled() && diagnosticsService.map(service -> service.tableReady("app_user")).orElse(false));
        details.put("authSessionTableAvailable", properties.isEnabled() && diagnosticsService.map(service -> service.tableReady("auth_session")).orElse(false));
        demoUserService.map(MysqlDemoUserService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("mysqlDemoUserLastError", error));
        businessSummaryService.map(MysqlBusinessSummaryService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("mysqlBusinessSummaryLastError", error));
        diagnosticsService.map(MysqlDiagnosticsService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("mysqlDiagnosticsLastError", error));
        historyMirrorService.map(MysqlHistoryMirrorService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("mysqlLastError", error));
        suggestionCacheService.map(MysqlSuggestionCacheService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("mysqlSuggestionCacheLastError", error));
        authService.map(MysqlAuthService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("authLastError", error));
        permissionService.map(MysqlPermissionService::lastError)
                .filter(error -> error != null && !error.isBlank())
                .ifPresent(error -> details.put("permissionLastError", error));

        if (!properties.isEnabled()) {
            details.put("mysqlStatus", "skipped");
            return details;
        }

        if (dataSource.isEmpty()) {
            details.put("mysqlStatus", "not-configured");
            return details;
        }

        try (Connection connection = dataSource.get().getConnection()) {
            details.put("mysqlAvailable", connection.isValid(1));
            details.put("mysqlStatus", Boolean.TRUE.equals(details.get("mysqlAvailable")) ? "available" : "unavailable");
        } catch (Exception ex) {
            details.put("mysqlStatus", "unavailable");
            details.put("mysqlError", summarize(ex));
            details.put("mysqlLastError", summarize(ex));
        }
        return details;
    }

    private String summarize(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 240 ? message.substring(0, 240) + "..." : message;
    }
}
